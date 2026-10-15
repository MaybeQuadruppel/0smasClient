package com.OsamaClient.newbridge.Hacks.Donut;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the map page in two flavors from the same template:
 * - {@link #export}: a static, self-contained HTML file with the data baked in at export time - works
 *   fully offline (marking points + radius works too, only "send to RtpFinder" needs the live page).
 * - {@link #liveHtml}: served by {@link RtpLiveServer}; starts with an empty data array, polls "/data"
 *   and "/status" itself, and can send a marked target (point + radius) to the {@link RtpFinder}.
 *
 * The region background (9x9 grid, borders, names) comes from {@link RtpRegionGrid#toJson()}, so the
 * browser and the RtpFinder always use the very same region layout.
 *
 * Every line in the JSONL log is already a valid JSON object, so for the static export we don't even
 * parse it - just join the lines with commas and wrap them into a JS array literal.
 */
public final class RtpMapExporter {

    private RtpMapExporter() {}

    public static void export(Path jsonl, Path outHtml) throws IOException {
        String html = fill(false, "", readDataArray(jsonl));
        Files.createDirectories(outHtml.getParent());
        Files.writeString(outHtml, html, StandardCharsets.UTF_8);
    }

    /** Page for {@link RtpLiveServer}: no baked-in data, polls itself; token authorizes target changes. */
    public static String liveHtml(String token) {
        return fill(true, token, "[]");
    }

    private static String fill(boolean live, String token, String dataArray) {
        return TEMPLATE
                .replace("/*__LIVE__*/", String.valueOf(live))
                .replace("/*__TOKEN__*/", token)
                .replace("/*__GRID__*/", RtpRegionGrid.toJson())
                .replace("/*__DATA__*/", dataArray);
    }

    private static String readDataArray(Path jsonl) throws IOException {
        List<String> lines = Files.exists(jsonl)
                ? Files.readAllLines(jsonl, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank()).collect(Collectors.toList())
                : List.of();
        return "[" + String.join(",\n", lines) + "]";
    }

    private static final String TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RTP Map</title>
<style>
  :root {
    color-scheme: dark;
    --bg: #0b0c10; --panel: #13151b; --panel2: #181b23; --line: #242834;
    --text: #e7e9ee; --muted: #8b90a0; --accent: #6C8CFF; --good: #4CD97B; --warn: #FFD166; --bad: #ff6b6b;
  }
  * { box-sizing: border-box; }
  html, body { height: 100%; }
  body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; background: var(--bg); color: var(--text); font-size: 13px; }
  #app { display: flex; height: 100vh; }

  /* -- Sidebar ----------------------------------------------- */
  #side { width: 340px; flex: none; overflow-y: auto; background: var(--panel); border-right: 1px solid var(--line); padding: 14px; }
  #side::-webkit-scrollbar { width: 8px; } #side::-webkit-scrollbar-thumb { background: #2a2e3a; border-radius: 4px; }
  h1 { font-size: 17px; margin: 0 0 2px; display: flex; align-items: center; gap: 8px; }
  #liveDot { width: 9px; height: 9px; border-radius: 50%; background: #3a3e4a; }
  #liveDot.on { background: var(--good); box-shadow: 0 0 8px var(--good); }
  #sub { color: var(--muted); font-size: 12px; margin-bottom: 12px; }
  .card { background: var(--panel2); border: 1px solid var(--line); border-radius: 10px; padding: 12px; margin-bottom: 12px; }
  h2 { font-size: 11px; text-transform: uppercase; letter-spacing: .07em; color: var(--muted); margin: 0 0 10px; font-weight: 600; }
  .hint { color: var(--muted); font-size: 12px; line-height: 1.45; margin: 0; }
  .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 3px 0; font-size: 12px; }
  .row .k { color: var(--muted); }
  .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  label.f { display: block; font-size: 11px; color: var(--muted); margin-bottom: 3px; }
  input[type=number], select { width: 100%; background: #10121a; color: var(--text); border: 1px solid #2b3040; border-radius: 6px; padding: 6px 8px; font: inherit; }
  input[type=number]:focus, select:focus { outline: none; border-color: var(--accent); }
  input[type=range] { width: 100%; accent-color: var(--accent); margin: 6px 0 2px; }
  button { font: inherit; cursor: pointer; border-radius: 6px; border: 1px solid #2f3445; background: #1c202b; color: var(--text); padding: 7px 10px; }
  button:hover:not(:disabled) { border-color: var(--accent); }
  button.primary { background: var(--accent); border-color: var(--accent); color: #0b0c10; font-weight: 600; }
  button.primary:hover:not(:disabled) { filter: brightness(1.1); }
  button:disabled { opacity: .45; cursor: not-allowed; }
  .btns { display: flex; gap: 8px; margin-top: 10px; }
  .btns button { flex: 1; }
  .swatch { display: inline-block; width: 11px; height: 11px; border-radius: 3px; margin-right: 7px; vertical-align: -1px; border: 1px solid rgba(0,0,0,.4); }
  .pill { display: inline-block; padding: 1px 7px; border-radius: 999px; font-size: 11px; background: #252a38; color: var(--text); }
  .pill.good { background: rgba(76,217,123,.18); color: var(--good); }
  .pill.warn { background: rgba(255,209,102,.16); color: var(--warn); }
  #overlapBar { display: flex; height: 8px; border-radius: 4px; overflow: hidden; margin: 6px 0 4px; background: #10121a; }
  #botStatus { margin-top: 10px; padding: 8px 10px; border-radius: 8px; background: #10121a; font-size: 12px; line-height: 1.5; }
  .quiet { font-size: 11px; padding: 5px 0; border-bottom: 1px solid #1f2330; cursor: pointer; display: flex; justify-content: space-between; }
  .quiet:hover { color: var(--accent); }
  [hidden] { display: none !important; }

  /* -- Map area ---------------------------------------------- */
  #mapwrap { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  #topbar { height: 46px; flex: none; display: flex; align-items: center; gap: 10px; padding: 0 14px; border-bottom: 1px solid var(--line); background: var(--panel); }
  #readout { flex: 1; font-family: ui-monospace, Consolas, monospace; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #readout .dim { color: var(--muted); }
  #topbar label.chk { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 12px; cursor: pointer; user-select: none; }
  #topbar button { padding: 4px 10px; }
  #stage { flex: 1; min-height: 0; display: flex; align-items: center; justify-content: center; padding: 10px; }
  canvas { display: block; border-radius: 10px; box-shadow: 0 10px 40px rgba(0,0,0,.45); cursor: crosshair; touch-action: none; }
  canvas.dragging { cursor: grabbing; }
  #tooltip { position: fixed; pointer-events: none; background: #0c0d12f2; border: 1px solid #333a4d; border-radius: 6px; padding: 6px 9px; font-size: 11px; display: none; white-space: nowrap; z-index: 10; }

  @media (max-width: 900px) {
    #app { flex-direction: column; height: auto; min-height: 100vh; }
    #side { width: auto; max-height: none; border-right: 0; border-bottom: 1px solid var(--line); }
    #stage { height: 100vw; flex: none; }
  }
</style>
</head>
<body>
<div id="app">
  <aside id="side">
    <h1><span id="liveDot"></span>RTP Map</h1>
    <div id="sub">Square world map, 9&times;9 region cells</div>

    <section class="card" id="targetCard">
      <h2>Mark target</h2>
      <p class="hint" id="targetHint">Click the map to set a point. Right-click removes it again.</p>
      <div id="targetBody" hidden>
        <div class="grid2">
          <div><label class="f" for="mX">X</label><input type="number" id="mX" step="100"></div>
          <div><label class="f" for="mZ">Z</label><input type="number" id="mZ" step="100"></div>
        </div>
        <label class="f" for="mRange" style="margin-top:10px">Radius: <b id="mRLabel"></b> blocks</label>
        <input type="range" id="mRange" min="0" max="1000" value="500">
        <input type="number" id="mR" min="50" step="100">

        <div class="row" style="margin-top:10px"><span class="k">Region at point</span><span id="mRegion"></span></div>
        <div class="row"><span class="k">Cell</span><span id="mCell"></span></div>
        <div class="k" style="color:var(--muted);font-size:11px;margin-top:6px">Circle lies in:</div>
        <div id="overlapBar"></div>
        <div id="overlapText" class="hint"></div>
        <div class="row" style="margin-top:8px"><span class="k">RTP-Region</span><span id="mBest"></span></div>
        <div class="row"><span class="k">Expected attempts</span><span id="mExpected"></span></div>
        <p class="hint" style="margin-top:4px">Estimate: RTP lands uniformly inside the region.</p>

        <div class="btns">
          <button class="primary" id="btnSend">Send to RtpFinder</button>
          <button id="btnClear">Clear</button>
        </div>
        <div id="sendHint" class="hint" style="margin-top:6px" hidden>Only available in live mode (enable Live Server in the module).</div>
        <div id="botStatus" hidden></div>
      </div>
    </section>

    <section class="card">
      <h2>View</h2>
      <label class="f" for="mode">Point display</label>
      <select id="mode">
        <option value="region">Points</option>
        <option value="heatmap">Heatmap (density)</option>
        <option value="height">Height (Y)</option>
        <option value="biome">Biome</option>
      </select>
      <label class="f" for="cellSize" style="margin-top:8px">Grid size for heatmap / quiet cells (blocks)</label>
      <input type="number" id="cellSize" value="5000" min="100" step="500">
    </section>

    <section class="card">
      <h2>Regions</h2>
      <div id="regionLegend"></div>
    </section>

    <section class="card">
      <h2>Success rate</h2>
      <div id="successStats"></div>
    </section>

    <section class="card">
      <h2>Quietest cells (base candidates)</h2>
      <div id="quietList" class="hint">waiting for data...</div>
    </section>
  </aside>

  <main id="mapwrap">
    <div id="topbar">
      <div id="readout"><span class="dim">Move the mouse over the map &hellip;</span></div>
      <label class="chk"><input type="checkbox" id="optGrid" checked>Grid</label>
      <label class="chk"><input type="checkbox" id="optNames" checked>Names</label>
      <label class="chk"><input type="checkbox" id="optIds">Cell numbers</label>
      <button id="zoomIn" title="Zoom in">+</button>
      <button id="zoomOut" title="Zoom out">&minus;</button>
      <button id="zoomReset" title="Whole map">All</button>
    </div>
    <div id="stage"><canvas id="c"></canvas></div>
  </main>
</div>
<div id="tooltip"></div>

<script>
(function () {
'use strict';

const LIVE  = /*__LIVE__*/;
const TOKEN = "/*__TOKEN__*/";
let DATA    = /*__DATA__*/;
const GRID  = /*__GRID__*/;

// ----------------------------- Region grid -----------------------------
const N = GRID.rows.length, CELL = GRID.cell, HALF = GRID.half, REG = GRID.regions;
const cellCount = new Array(REG.length).fill(0);
for (const row of GRID.rows) for (const r of row) cellCount[r]++;
const regionArea = cellCount.map(c => c * CELL * CELL);

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function cellOf(x, z) {
  return [clamp(Math.floor((x + HALF) / CELL), 0, N - 1), clamp(Math.floor((z + HALF) / CELL), 0, N - 1)];
}
function regionIdxAt(x, z) { const c = cellOf(x, z); return GRID.rows[c[1]][c[0]]; }
function inWorld(x, z) { return x >= -HALF && x <= HALF && z >= -HALF && z <= HALF; }

/** Wie viel vom Kreis liegt in welcher Region + Trefferchance pro RTP in diese Region. */
function circleStats(x, z, r) {
  const n = 64, counts = new Array(REG.length).fill(0);
  let total = 0;
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
    const px = x - r + (i + 0.5) / n * 2 * r, pz = z - r + (j + 0.5) / n * 2 * r;
    if ((px - x) * (px - x) + (pz - z) * (pz - z) > r * r) continue;
    if (!inWorld(px, pz)) continue;
    counts[regionIdxAt(px, pz)]++; total++;
  }
  const a = Math.pow(2 * r / n, 2);
  const list = [];
  for (let i = 0; i < REG.length; i++) {
    if (!counts[i]) continue;
    const area = counts[i] * a;
    list.push({ idx: i, share: total ? counts[i] / total : 0, p: area / regionArea[i] });
  }
  list.sort((p, q) => q.p - p.p);
  return list;
}

// Regionsgrenzen (Kanten zwischen Feldern mit unterschiedlicher Region) + Beschriftungspunkte
const BORDERS = [];
for (let cz = 0; cz < N; cz++) for (let cx = 0; cx < N; cx++) {
  const x0 = -HALF + cx * CELL, z0 = -HALF + cz * CELL;
  if (cx < N - 1 && GRID.rows[cz][cx] !== GRID.rows[cz][cx + 1]) BORDERS.push([x0 + CELL, z0, x0 + CELL, z0 + CELL]);
  if (cz < N - 1 && GRID.rows[cz][cx] !== GRID.rows[cz + 1][cx]) BORDERS.push([x0, z0 + CELL, x0 + CELL, z0 + CELL]);
}
const LABELS = (function () {
  const seen = Array.from({ length: N }, () => new Array(N).fill(false));
  const out = [];
  for (let cz = 0; cz < N; cz++) for (let cx = 0; cx < N; cx++) {
    if (seen[cz][cx]) continue;
    const reg = GRID.rows[cz][cx], comp = [], q = [[cx, cz]];
    seen[cz][cx] = true;
    while (q.length) {
      const c = q.pop(); comp.push(c);
      for (const d of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = c[0] + d[0], nz = c[1] + d[1];
        if (nx < 0 || nz < 0 || nx >= N || nz >= N || seen[nz][nx] || GRID.rows[nz][nx] !== reg) continue;
        seen[nz][nx] = true; q.push([nx, nz]);
      }
    }
    if (comp.length < 2 && cellCount[reg] > 1) continue;
    let mx = 0, mz = 0;
    for (const c of comp) { mx += c[0]; mz += c[1]; }
    mx /= comp.length; mz /= comp.length;
    let best = comp[0], bd = 1e9;
    for (const c of comp) { const d = Math.hypot(c[0] - mx, c[1] - mz); if (d < bd) { bd = d; best = c; } }
    out.push({ reg, x: -HALF + (best[0] + 0.5) * CELL, z: -HALF + (best[1] + 0.5) * CELL });
  }
  return out;
})();

// ----------------------------- DOM / state -----------------------------
const $ = id => document.getElementById(id);
const canvas = $('c'), ctx = canvas.getContext('2d');
const stage = $('stage');

let view = { cx: 0, cz: 0, half: HALF };
let S = 600, dpr = 1, MAPX = 64, MAPY = 12, MAPA = 520, sc = 1;
const ML = 64, MR = 12, MT = 12, MB = 64;   // ML+MR == MT+MB  ->  Karte bleibt exakt quadratisch

let successPoints = [], focusPoint = null, hover = null, marker = null, botState = null, adopted = false;
let raf = 0;

function schedule() { if (!raf) raf = requestAnimationFrame(() => { raf = 0; render(); }); }

function layout() {
  const side = Math.max(300, Math.floor(Math.min(stage.clientWidth, stage.clientHeight) - 20));
  S = side; dpr = window.devicePixelRatio || 1;
  canvas.style.width = S + 'px'; canvas.style.height = S + 'px';
  canvas.width = Math.round(S * dpr); canvas.height = Math.round(S * dpr);
  MAPX = ML; MAPY = MT; MAPA = S - ML - MR;
  schedule();
}

function clampView() {
  view.half = clamp(view.half, 400, HALF);
  const lim = HALF - view.half;
  view.cx = clamp(view.cx, -lim, lim); view.cz = clamp(view.cz, -lim, lim);
}
function w2s(x, z) { return [MAPX + (x - (view.cx - view.half)) * sc, MAPY + (z - (view.cz - view.half)) * sc]; }
function s2w(sx, sy) { return [(sx - MAPX) / sc + view.cx - view.half, (sy - MAPY) / sc + view.cz - view.half]; }
function insideMap(sx, sy) { return sx >= MAPX && sy >= MAPY && sx <= MAPX + MAPA && sy <= MAPY + MAPA; }

function niceStep(minPx) {
  const target = minPx / sc;
  const steps = [100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 25000, 50000, 100000];
  for (const s of steps) if (s >= target) return s;
  return 100000;
}
function fmtAxis(v) {
  if (Math.abs(v) >= 1000 && v % 1000 === 0) return (v / 1000) + 'k';
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + 'k';
  return String(v);
}
function hashColor(str) {
  let h = 0; for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  return 'hsl(' + (Math.abs(h) % 360) + ', 65%, 60%)';
}

// ----------------------------- Rendering -----------------------------
const opt = { grid: true, names: true, ids: false };

function render() {
  if (!ctx) return;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.fillStyle = '#0b0c10'; ctx.fillRect(0, 0, S, S);
  sc = MAPA / (2 * view.half);

  ctx.save();
  ctx.beginPath(); ctx.rect(MAPX, MAPY, MAPA, MAPA); ctx.clip();
  drawRegions();
  if ($('mode').value === 'heatmap') drawHeatmap();
  if (opt.grid) drawGrid();
  drawBorders();
  if (opt.names) drawNames();
  drawPoints();
  drawOverlays();
  drawCrosshair();
  ctx.restore();

  ctx.strokeStyle = '#2a2e3a'; ctx.lineWidth = 1;
  ctx.strokeRect(MAPX - 0.5, MAPY - 0.5, MAPA + 1, MAPA + 1);
  drawAxes();
}

function drawRegions() {
  const cellPx = CELL * sc;
  for (let cz = 0; cz < N; cz++) for (let cx = 0; cx < N; cx++) {
    const [sx, sy] = w2s(-HALF + cx * CELL, -HALF + cz * CELL);
    if (sx > MAPX + MAPA || sy > MAPY + MAPA || sx + cellPx < MAPX || sy + cellPx < MAPY) continue;
    ctx.fillStyle = REG[GRID.rows[cz][cx]].color;
    ctx.fillRect(sx, sy, cellPx + 0.6, cellPx + 0.6);
    if (opt.ids && cellPx > 34) {
      ctx.fillStyle = 'rgba(255,255,255,.85)';
      ctx.font = 'bold ' + Math.min(20, cellPx * 0.26) + 'px system-ui, sans-serif';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(String(GRID.ids[cz][cx]), sx + cellPx / 2, sy + cellPx / 2);
    }
  }
}

function drawGrid() {
  const step = niceStep(60);
  const x0 = view.cx - view.half, x1 = view.cx + view.half, z0 = view.cz - view.half, z1 = view.cz + view.half;
  ctx.strokeStyle = 'rgba(0,0,0,.20)'; ctx.lineWidth = 1;
  ctx.beginPath();
  for (let i = Math.floor((x0 + HALF) / step); i <= Math.ceil((x1 + HALF) / step); i++) {
    const sx = Math.round(w2s(-HALF + i * step, 0)[0]) + 0.5; ctx.moveTo(sx, MAPY); ctx.lineTo(sx, MAPY + MAPA);
  }
  for (let i = Math.floor((z0 + HALF) / step); i <= Math.ceil((z1 + HALF) / step); i++) {
    const sy = Math.round(w2s(0, -HALF + i * step)[1]) + 0.5; ctx.moveTo(MAPX, sy); ctx.lineTo(MAPX + MAPA, sy);
  }
  ctx.stroke();
}

function drawBorders() {
  const paint = (w, style) => {
    ctx.strokeStyle = style; ctx.lineWidth = w; ctx.lineCap = 'square';
    ctx.beginPath();
    for (const b of BORDERS) { const a = w2s(b[0], b[1]), c = w2s(b[2], b[3]); ctx.moveTo(a[0], a[1]); ctx.lineTo(c[0], c[1]); }
    ctx.stroke();
  };
  paint(6, 'rgba(0,0,0,.45)');
  paint(2.5, 'rgba(255,255,255,.95)');
  // Weltrand
  const a = w2s(-HALF, -HALF), c = w2s(HALF, HALF);
  ctx.strokeStyle = 'rgba(0,0,0,.7)'; ctx.lineWidth = 4;
  ctx.strokeRect(a[0], a[1], c[0] - a[0], c[1] - a[1]);
}

function drawNames() {
  const cellPx = CELL * sc;
  if (cellPx < 40) return;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.font = 'bold ' + clamp(cellPx * 0.17, 11, 26) + 'px system-ui, sans-serif';
  for (const l of LABELS) {
    const [sx, sy] = w2s(l.x, l.z);
    if (sx < MAPX - 100 || sx > MAPX + MAPA + 100 || sy < MAPY - 40 || sy > MAPY + MAPA + 40) continue;
    const t = REG[l.reg].name.toUpperCase();
    ctx.lineWidth = 4; ctx.strokeStyle = 'rgba(0,0,0,.55)'; ctx.strokeText(t, sx, sy);
    ctx.fillStyle = '#fff'; ctx.fillText(t, sx, sy);
  }
}

function cellSizeInput() { return Math.max(100, parseInt($('cellSize').value) || 5000); }

function buildGrid(cs) {
  const g = new Map();
  for (const d of successPoints) {
    const gx = Math.floor(d.x / cs), gz = Math.floor(d.z / cs), key = gx + '_' + gz;
    if (!g.has(key)) g.set(key, { gx, gz, count: 0 });
    g.get(key).count++;
  }
  return g;
}

function drawHeatmap() {
  const cs = cellSizeInput(), g = buildGrid(cs);
  let max = 1; for (const c of g.values()) max = Math.max(max, c.count);
  const px = cs * sc;
  for (const c of g.values()) {
    const [sx, sy] = w2s(c.gx * cs, c.gz * cs), t = c.count / max;
    ctx.fillStyle = 'hsla(' + ((1 - t) * 220) + ', 85%, 50%, ' + (0.35 + t * 0.55) + ')';
    ctx.fillRect(sx, sy, px, px);
  }
}

function drawPoints() {
  const mode = $('mode').value;
  let minY = Infinity, maxY = -Infinity;
  if (mode === 'height') for (const d of successPoints) { minY = Math.min(minY, d.y); maxY = Math.max(maxY, d.y); }
  const rad = clamp(3 + (HALF / view.half) * 0.15, 3, 6);
  ctx.lineWidth = 1.5; ctx.strokeStyle = '#0b0c10';
  for (const d of successPoints) {
    const [sx, sy] = w2s(d.x, d.z);
    if (sx < MAPX - 6 || sy < MAPY - 6 || sx > MAPX + MAPA + 6 || sy > MAPY + MAPA + 6) continue;
    let color = '#ffffff';
    if (mode === 'biome') color = hashColor(d.biome || '?');
    else if (mode === 'height') { const t = maxY > minY ? (d.y - minY) / (maxY - minY) : 0.5; color = 'hsl(' + (220 - t * 220) + ', 75%, 58%)'; }
    else if (mode === 'heatmap') color = 'rgba(255,255,255,.85)';
    ctx.fillStyle = color;
    ctx.beginPath(); ctx.arc(sx, sy, mode === 'heatmap' ? 2.5 : rad, 0, Math.PI * 2); ctx.fill();
    if (mode !== 'heatmap') ctx.stroke();
  }
}

function drawOverlays() {
  if (focusPoint) {
    const [sx, sy] = w2s(focusPoint[0], focusPoint[1]);
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(sx, sy, 11, 0, Math.PI * 2); ctx.stroke();
  }
  if (botState && botState.lastX !== undefined && botState.attempts > 0 && botState.hasTarget) {
    const [sx, sy] = w2s(botState.lastX, botState.lastZ);
    ctx.fillStyle = '#FFD166'; ctx.strokeStyle = '#0b0c10'; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(sx, sy - 7); ctx.lineTo(sx + 7, sy); ctx.lineTo(sx, sy + 7); ctx.lineTo(sx - 7, sy); ctx.closePath(); ctx.fill(); ctx.stroke();
  }
  if (!marker) return;
  const [sx, sy] = w2s(marker.x, marker.z), rp = marker.r * sc;
  ctx.fillStyle = 'rgba(255,255,255,.14)';
  ctx.beginPath(); ctx.arc(sx, sy, rp, 0, Math.PI * 2); ctx.fill();
  ctx.setLineDash([7, 5]); ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(0,0,0,.6)'; ctx.stroke();
  ctx.lineWidth = 1.6; ctx.strokeStyle = '#fff'; ctx.stroke(); ctx.setLineDash([]);
  // Fadenkreuz-Pin
  ctx.strokeStyle = '#0b0c10'; ctx.lineWidth = 4;
  ctx.beginPath(); ctx.moveTo(sx - 12, sy); ctx.lineTo(sx + 12, sy); ctx.moveTo(sx, sy - 12); ctx.lineTo(sx, sy + 12); ctx.stroke();
  ctx.strokeStyle = '#FFD166'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.moveTo(sx - 12, sy); ctx.lineTo(sx + 12, sy); ctx.moveTo(sx, sy - 12); ctx.lineTo(sx, sy + 12); ctx.stroke();
  ctx.fillStyle = '#FFD166'; ctx.strokeStyle = '#0b0c10'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.arc(sx, sy, 4.5, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
}

function drawCrosshair() {
  if (!hover || !hover.inside) return;
  ctx.strokeStyle = 'rgba(255,255,255,.4)'; ctx.lineWidth = 1; ctx.setLineDash([4, 4]);
  ctx.beginPath(); ctx.moveTo(hover.sx, MAPY); ctx.lineTo(hover.sx, MAPY + MAPA); ctx.moveTo(MAPX, hover.sy); ctx.lineTo(MAPX + MAPA, hover.sy); ctx.stroke();
  ctx.setLineDash([]);
}

function drawAxes() {
  const step = niceStep(68);
  const x0 = view.cx - view.half, x1 = view.cx + view.half, z0 = view.cz - view.half, z1 = view.cz + view.half;
  ctx.font = '11px ui-monospace, Consolas, monospace';
  ctx.fillStyle = '#9aa0b2'; ctx.strokeStyle = '#3a3f50'; ctx.lineWidth = 1;

  ctx.textAlign = 'center'; ctx.textBaseline = 'top';
  for (let i = Math.floor((x0 + HALF) / step); i <= Math.ceil((x1 + HALF) / step); i++) {
    const v = -HALF + i * step, sx = w2s(v, 0)[0];
    if (sx < MAPX - 1 || sx > MAPX + MAPA + 1) continue;
    ctx.beginPath(); ctx.moveTo(sx + 0.5, MAPY + MAPA); ctx.lineTo(sx + 0.5, MAPY + MAPA + 5); ctx.stroke();
    ctx.fillText(fmtAxis(v), sx, MAPY + MAPA + 8);
  }
  ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
  for (let i = Math.floor((z0 + HALF) / step); i <= Math.ceil((z1 + HALF) / step); i++) {
    const v = -HALF + i * step, sy = w2s(0, v)[1];
    if (sy < MAPY - 1 || sy > MAPY + MAPA + 1) continue;
    ctx.beginPath(); ctx.moveTo(MAPX - 5, sy + 0.5); ctx.lineTo(MAPX, sy + 0.5); ctx.stroke();
    ctx.fillText(fmtAxis(v), MAPX - 8, sy);
  }

  // Achsentitel
  ctx.fillStyle = '#c9cede'; ctx.font = 'bold 12px system-ui, sans-serif';
  ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
  ctx.fillText('X   West \\u25C4  \\u25BA East', MAPX + MAPA / 2, S - 10);
  ctx.save(); ctx.translate(14, MAPY + MAPA / 2); ctx.rotate(-Math.PI / 2);
  ctx.fillText('Z   North \\u25C4  \\u25BA South', 0, 0); ctx.restore();

  // Werte der Mausposition direkt an den Achsen
  if (hover && hover.inside) {
    ctx.font = 'bold 11px ui-monospace, Consolas, monospace';
    const tx = String(Math.round(hover.wx)), tz = String(Math.round(hover.wz));
    const wx = ctx.measureText(tx).width + 10, wz = ctx.measureText(tz).width + 10;
    ctx.fillStyle = '#FFD166'; ctx.fillRect(hover.sx - wx / 2, MAPY + MAPA + 6, wx, 17);
    ctx.fillRect(MAPX - 6 - wz, hover.sy - 8.5, wz, 17);
    ctx.fillStyle = '#0b0c10'; ctx.textBaseline = 'middle';
    ctx.textAlign = 'center'; ctx.fillText(tx, hover.sx, MAPY + MAPA + 15);
    ctx.textAlign = 'right'; ctx.fillText(tz, MAPX - 11, hover.sy);
  }
}

// ----------------------------- Interaction -----------------------------
let drag = null;
const tooltip = $('tooltip'), readout = $('readout');

function updateHover(sx, sy, ev) {
  const inside = insideMap(sx, sy);
  const w = s2w(sx, sy);
  hover = { sx, sy, wx: w[0], wz: w[1], inside };
  if (!inside) { readout.innerHTML = '<span class="dim">Move the mouse over the map &hellip;</span>'; tooltip.style.display = 'none'; return; }
  const c = cellOf(w[0], w[1]), reg = REG[GRID.rows[c[1]][c[0]]];
  readout.innerHTML = 'X <b>' + Math.round(w[0]) + '</b> &nbsp; Z <b>' + Math.round(w[1]) + '</b> &nbsp;<span class="dim">|</span>&nbsp; '
    + '<span class="swatch" style="background:' + reg.color + '"></span>' + reg.name + ' <span class="dim">(cell ' + GRID.ids[c[1]][c[0]] + ')</span>';

  let nearest = null, nd = 11;
  for (const d of successPoints) {
    const p = w2s(d.x, d.z), dist = Math.hypot(p[0] - sx, p[1] - sy);
    if (dist < nd) { nd = dist; nearest = d; }
  }
  if (nearest && ev) {
    tooltip.style.display = 'block';
    tooltip.style.left = (ev.clientX + 14) + 'px'; tooltip.style.top = (ev.clientY + 14) + 'px';
    tooltip.innerHTML = '<b>' + nearest.region + '</b><br>' + nearest.x + ', ' + nearest.y + ', ' + nearest.z + '<br>' + (nearest.biome || '');
  } else tooltip.style.display = 'none';
}

canvas.addEventListener('pointerdown', e => {
  if (e.button !== 0) return;
  canvas.setPointerCapture(e.pointerId);
  drag = { sx: e.offsetX, sy: e.offsetY, cx: view.cx, cz: view.cz, moved: false };
});
canvas.addEventListener('pointermove', e => {
  if (drag) {
    const dx = e.offsetX - drag.sx, dy = e.offsetY - drag.sy;
    if (!drag.moved && Math.hypot(dx, dy) > 4) { drag.moved = true; canvas.classList.add('dragging'); }
    if (drag.moved) { view.cx = drag.cx - dx / sc; view.cz = drag.cz - dy / sc; clampView(); }
  }
  updateHover(e.offsetX, e.offsetY, e); schedule();
});
canvas.addEventListener('pointerup', e => {
  if (drag && !drag.moved && insideMap(e.offsetX, e.offsetY)) {
    const w = s2w(e.offsetX, e.offsetY);
    setMarker(clamp(w[0], -HALF, HALF), clamp(w[1], -HALF, HALF));
  }
  drag = null; canvas.classList.remove('dragging');
});
canvas.addEventListener('pointerleave', () => { hover = null; tooltip.style.display = 'none'; schedule(); });
canvas.addEventListener('contextmenu', e => { e.preventDefault(); clearMarker(); });
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  if (!insideMap(e.offsetX, e.offsetY)) return;
  zoomAt(e.offsetX, e.offsetY, e.deltaY < 0 ? 1 / 1.25 : 1.25);
}, { passive: false });

function zoomAt(sx, sy, f) {
  const w = s2w(sx, sy);
  view.half = clamp(view.half * f, 400, HALF);
  const sc2 = MAPA / (2 * view.half);
  view.cx = w[0] - (sx - MAPX) / sc2 + view.half;
  view.cz = w[1] - (sy - MAPY) / sc2 + view.half;
  clampView(); schedule();
}
$('zoomIn').onclick = () => zoomAt(MAPX + MAPA / 2, MAPY + MAPA / 2, 1 / 1.6);
$('zoomOut').onclick = () => zoomAt(MAPX + MAPA / 2, MAPY + MAPA / 2, 1.6);
$('zoomReset').onclick = () => { view = { cx: 0, cz: 0, half: HALF }; schedule(); };
window.addEventListener('keydown', e => { if (e.key === 'Escape') clearMarker(); });

for (const [id, key] of [['optGrid', 'grid'], ['optNames', 'names'], ['optIds', 'ids']]) {
  $(id).checked = opt[key];
  $(id).addEventListener('change', () => { opt[key] = $(id).checked; schedule(); });
}
$('mode').addEventListener('change', schedule);
$('cellSize').addEventListener('input', () => { renderQuietList(); schedule(); });

// ----------------------------- Marker / Ziel -----------------------------
const R_MIN = 100, R_MAX = 120000;
const rFromSlider = t => Math.round(R_MIN * Math.pow(R_MAX / R_MIN, t / 1000) / 50) * 50;
const sliderFromR = r => Math.round(1000 * Math.log(r / R_MIN) / Math.log(R_MAX / R_MIN));

function setMarker(x, z, r) {
  marker = { x: Math.round(x), z: Math.round(z), r: r || (marker ? marker.r : 5000) };
  saveMarker(); updatePanel(true); schedule();
}
function clearMarker(skipServer) {
  marker = null; saveMarker(); updatePanel(true); schedule();
  if (LIVE && !skipServer) api('DELETE', '/target').then(pollStatus).catch(() => {});
}
function saveMarker() { try { localStorage.setItem('rtpMarker', JSON.stringify(marker)); } catch (e) {} }
function loadMarker() { try { const m = JSON.parse(localStorage.getItem('rtpMarker')); if (m && isFinite(m.x) && isFinite(m.z) && m.r > 0) marker = m; } catch (e) {} }

function fmtCount(n) {
  if (!isFinite(n)) return '&infin;';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + ' M';
  if (n >= 1e4) return Math.round(n / 1000) + 'k';
  if (n >= 100) return String(Math.round(n));
  return n.toFixed(1);
}

function updatePanel(force) {
  $('targetBody').hidden = !marker;
  $('targetHint').hidden = !!marker;
  if (!marker) return;
  const setIfIdle = (el, v) => { if (force || document.activeElement !== el) el.value = v; };
  setIfIdle($('mX'), marker.x); setIfIdle($('mZ'), marker.z); setIfIdle($('mR'), marker.r);
  $('mRange').value = sliderFromR(marker.r);
  $('mRLabel').textContent = marker.r;

  const c = cellOf(marker.x, marker.z), reg = REG[GRID.rows[c[1]][c[0]]];
  $('mRegion').innerHTML = '<span class="swatch" style="background:' + reg.color + '"></span>' + reg.name;
  $('mCell').textContent = '#' + GRID.ids[c[1]][c[0]];

  const st = circleStats(marker.x, marker.z, marker.r);
  const total = st.reduce((a, s) => a + s.share, 0) || 1;
  $('overlapBar').innerHTML = st.map(s => '<div style="width:' + (s.share / total * 100) + '%;background:' + REG[s.idx].color + '"></div>').join('');
  $('overlapText').innerHTML = st.map(s => '<span class="swatch" style="background:' + REG[s.idx].color + '"></span>' + REG[s.idx].name + ' ' + Math.round(s.share / total * 100) + '%').join(' &nbsp; ');
  if (st.length) {
    const b = st[0];
    $('mBest').innerHTML = '<span class="pill good">' + REG[b.idx].token + '</span>';
    $('mExpected').textContent = '~' + fmtCount(1 / b.p).replace('&infin;', 'infinite');
  } else {
    $('mBest').textContent = '-'; $('mExpected').textContent = '-';
  }
  $('btnSend').disabled = !LIVE;
  $('sendHint').hidden = LIVE;
  $('botStatus').hidden = !LIVE;
}

function readInputs() {
  const x = parseFloat($('mX').value), z = parseFloat($('mZ').value), r = parseFloat($('mR').value);
  if (isFinite(x) && isFinite(z)) marker = { x: Math.round(clamp(x, -HALF, HALF)), z: Math.round(clamp(z, -HALF, HALF)), r: isFinite(r) && r > 0 ? clamp(r, 50, 250000) : (marker ? marker.r : 5000) };
  saveMarker(); updatePanel(false); schedule();
}
for (const id of ['mX', 'mZ', 'mR']) $(id).addEventListener('input', readInputs);
$('mRange').addEventListener('input', () => { if (marker) { marker.r = rFromSlider(parseInt($('mRange').value)); saveMarker(); updatePanel(true); schedule(); } });
$('btnClear').onclick = () => clearMarker();
$('btnSend').onclick = () => {
  if (!marker) return;
  $('btnSend').disabled = true;
  api('POST', '/target', { x: marker.x, z: marker.z, r: marker.r }).then(pollStatus)
    .catch(() => { $('botStatus').textContent = 'Sending failed.'; })
    .finally(() => { $('btnSend').disabled = !LIVE; });
};

function api(method, path, body) {
  return fetch(path, {
    method, cache: 'no-store',
    headers: { 'Content-Type': 'application/json', 'X-Rtp-Token': TOKEN },
    body: body ? JSON.stringify(body) : undefined
  }).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
}

function renderBotStatus() {
  const el = $('botStatus');
  if (!LIVE || !marker) return;
  if (!botState || !botState.hasTarget) { el.innerHTML = '<span class="pill">No target set</span><br>The RtpFinder is currently rolling freely through all regions.'; return; }
  const same = marker.x === botState.x && marker.z === botState.z && marker.r === botState.r;
  let html = botState.reached
    ? '<span class="pill good">Target reached</span>'
    : '<span class="pill warn">' + (botState.enabled ? 'Bot searching &hellip;' : 'Target set (module off)') + '</span>';
  html += '<br>Target: ' + botState.x + ', ' + botState.z + ' (r ' + botState.r + ')' + (same ? '' : ' <span class="pill">differs from marker</span>');
  html += '<br>Region: <b>' + botState.region + '</b> &middot; Attempts: <b>' + botState.attempts + '</b>';
  if (botState.lastDist >= 0) html += '<br>Last landing: ' + botState.lastX + ', ' + botState.lastZ + ' (distance ' + Math.round(botState.lastDist) + ')';
  el.innerHTML = html;
}

function pollStatus() {
  if (!LIVE) return Promise.resolve();
  return fetch('/status', { cache: 'no-store' }).then(r => r.json()).then(s => {
    botState = s;
    if (!adopted && s.hasTarget && !marker) { adopted = true; marker = { x: s.x, z: s.z, r: s.r }; saveMarker(); updatePanel(true); }
    renderBotStatus(); schedule();
  }).catch(() => {});
}

// ----------------------------- Daten / Seitenleiste -----------------------------
function computeData() {
  successPoints = DATA.filter(d => d.success);
  const ok = successPoints.length, total = DATA.length;
  $('successStats').innerHTML = '<div class="row"><span class="k">Successful</span><span>' + ok + ' / ' + total + ' (' + (total ? (ok / total * 100).toFixed(1) : '0.0') + '%)</span></div>';

  const counts = {};
  for (const d of successPoints) counts[d.region] = (counts[d.region] || 0) + 1;
  $('regionLegend').innerHTML = REG.map(r => {
    const n = counts[r.token] || 0;
    return '<div class="row"><span><span class="swatch" style="background:' + r.color + '"></span>' + r.name + '</span><span>' + n + (ok ? ' (' + (n / ok * 100).toFixed(1) + '%)' : '') + '</span></div>';
  }).join('');
  renderQuietList();
}

function renderQuietList() {
  const el = $('quietList'), cs = cellSizeInput(), g = buildGrid(cs);
  const cells = [...g.values()].filter(c => {
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
      if ((dx || dz) && g.has((c.gx + dx) + '_' + (c.gz + dz))) return true;
    }
    return false;
  }).sort((a, b) => a.count - b.count).slice(0, 10);
  if (!cells.length) { el.textContent = 'not enough data yet'; return; }
  el.innerHTML = '';
  for (const c of cells) {
    const cx = Math.round((c.gx + 0.5) * cs), cz = Math.round((c.gz + 0.5) * cs);
    const div = document.createElement('div');
    div.className = 'quiet';
    div.innerHTML = '<span>' + cx + ', ' + cz + '</span><span>' + c.count + 'x visited</span>';
    div.onclick = () => {
      focusPoint = [cx, cz]; view.cx = cx; view.cz = cz; view.half = Math.min(view.half, Math.max(cs * 4, 2000)); clampView();
      setMarker(cx, cz, Math.max(200, Math.round(cs / 2)));
    };
    el.appendChild(div);
  }
}

function poll() {
  return fetch('/data', { cache: 'no-store' }).then(r => r.json()).then(d => {
    DATA = d; $('liveDot').classList.add('on'); computeData(); schedule();
  }).catch(() => $('liveDot').classList.remove('on'));
}

// ----------------------------- Start -----------------------------
loadMarker();
computeData();
updatePanel(true);
if (window.ResizeObserver) new ResizeObserver(layout).observe(stage); else window.addEventListener('resize', layout);
layout();
if (LIVE) {
  poll(); pollStatus();
  setInterval(poll, 3000);
  setInterval(pollStatus, 2000);
} else {
  $('sub').textContent = 'Static export (snapshot)';
}
})();
</script>
</body>
</html>
""";
}