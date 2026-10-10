package com.OsamaClient.newbridge.Hacks.Donut;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tiny local-only HTTP server (JDK's own {@code com.sun.net.httpserver}, no extra dependency) so the map
 * page can poll fresh data instead of being a one-shot export. Binds to 127.0.0.1 only - never reachable
 * from outside your own machine.
 *
 * Endpoints:
 *  GET    /        the live HTML page (contains this session's token)
 *  GET    /data    the current JSONL log as a JSON array
 *  GET    /status  the RtpFinder's target state (see {@link RtpFinder#statusJson()})
 *  POST   /target  {"x":..,"z":..,"r":..}  sets the target area (point + radius) for the RtpFinder
 *  DELETE /target  clears the target
 *
 * Changing the target needs the per-session token (header X-Rtp-Token, only known to the page this server
 * itself served), and every request's Host/Origin must be this server. That keeps other websites open in
 * your browser from poking at localhost:PORT (CSRF / DNS rebinding) and steering your bot.
 */
public final class RtpLiveServer {

    private static final int MAX_BODY = 2048;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static HttpServer server;
    private static int port;
    private static String token = "";

    /** Updated by {@link RtpFinder} whenever it logs an attempt - the server always serves whatever
     *  file was last written to, so the page still works if you're not currently connected. */
    static volatile Path logFile;

    private RtpLiveServer() {}

    public static synchronized boolean isRunning() {
        return server != null;
    }

    public static synchronized void start(int listenPort) {
        if (server != null) return;
        try {
            port = listenPort;
            token = newToken();
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), listenPort), 0);

            server.createContext("/", ex -> guarded(ex, () -> {
                if (!method(ex, "GET")) return;
                respond(ex, 200, "text/html; charset=utf-8", RtpMapExporter.liveHtml(token).getBytes(StandardCharsets.UTF_8));
            }));
            server.createContext("/data", ex -> guarded(ex, () -> {
                if (!method(ex, "GET")) return;
                respond(ex, 200, "application/json; charset=utf-8", currentDataJson().getBytes(StandardCharsets.UTF_8));
            }));
            server.createContext("/status", ex -> guarded(ex, () -> {
                if (!method(ex, "GET")) return;
                respond(ex, 200, "application/json; charset=utf-8", RtpFinder.statusJson().getBytes(StandardCharsets.UTF_8));
            }));
            server.createContext("/target", ex -> guarded(ex, () -> handleTarget(ex)));

            server.setExecutor(null); // default executor is fine, this is barely any load
            server.start();
        } catch (IOException e) {
            server = null; // port probably taken - module just won't have a live view this session
        }
    }

    public static synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        server = null;
    }

    // ─── /target ──────────────────────────────────────────────────────────────

    private static void handleTarget(HttpExchange ex) throws IOException {
        String m = ex.getRequestMethod();
        if (!m.equals("POST") && !m.equals("DELETE")) {
            ex.getResponseHeaders().set("Allow", "POST, DELETE");
            respond(ex, 405, "text/plain; charset=utf-8", "method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!tokenOk(ex)) {
            respond(ex, 403, "text/plain; charset=utf-8", "forbidden".getBytes(StandardCharsets.UTF_8));
            return;
        }

        if (m.equals("DELETE")) {
            RtpFinder.clearTarget();
        } else {
            byte[] raw = ex.getRequestBody().readNBytes(MAX_BODY + 1);
            if (raw.length > MAX_BODY) {
                respond(ex, 413, "text/plain; charset=utf-8", "too large".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String body = new String(raw, StandardCharsets.UTF_8);
            Double x = number(body, "x"), z = number(body, "z"), r = number(body, "r");
            if (x == null || z == null || r == null || r < 50 || r > 250_000) {
                respond(ex, 400, "text/plain; charset=utf-8", "bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            RtpFinder.setTarget((int) Math.round(x), (int) Math.round(z), (int) Math.round(r));
        }
        respond(ex, 200, "application/json; charset=utf-8", RtpFinder.statusJson().getBytes(StandardCharsets.UTF_8));
    }

    private static Double number(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(" + NUMBER.pattern() + ")").matcher(body);
        if (!m.find()) return null;
        try {
            double v = Double.parseDouble(m.group(1));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ─── Security helpers ─────────────────────────────────────────────────────

    private interface Body { void run() throws IOException; }

    /** Host/Origin check for every request + no exception ever leaks out of the server thread. */
    private static void guarded(HttpExchange ex, Body body) throws IOException {
        try {
            if (!hostOk(ex)) {
                respond(ex, 403, "text/plain; charset=utf-8", "forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            body.run();
        } catch (RuntimeException e) {
            respond(ex, 500, "text/plain; charset=utf-8", "error".getBytes(StandardCharsets.UTF_8));
        } finally {
            ex.close();
        }
    }

    private static boolean hostOk(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("localhost:" + port) && !host.equals("127.0.0.1:" + port)) return false; // DNS rebinding
        String origin = ex.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.toLowerCase(Locale.ROOT).equals("http://" + host);          // CSRF
    }

    private static boolean tokenOk(HttpExchange ex) {
        String given = ex.getRequestHeaders().getFirst("X-Rtp-Token");
        return given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean method(HttpExchange ex, String expected) throws IOException {
        if (ex.getRequestMethod().equals(expected)) return true;
        ex.getResponseHeaders().set("Allow", expected);
        respond(ex, 405, "text/plain; charset=utf-8", "method not allowed".getBytes(StandardCharsets.UTF_8));
        return false;
    }

    private static String newToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // ─── Data / response ──────────────────────────────────────────────────────

    private static String currentDataJson() {
        Path file = logFile;
        try {
            if (file == null || !Files.exists(file)) return "[]";
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(l -> !l.isBlank()).collect(Collectors.toList());
            return "[" + String.join(",", lines) + "]";
        } catch (IOException e) {
            return "[]";
        }
    }

    private static void respond(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (contentType.startsWith("text/html")) {
            ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:");
        }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}