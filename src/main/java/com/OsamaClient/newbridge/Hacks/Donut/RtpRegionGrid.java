package com.OsamaClient.newbridge.Hacks.Donut;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Regionskarte von DonutSMP als 9x9-Raster (aus dem Regions-Bild abgelesen).
 *
 * Jedes Feld ist {@link #CELL} Blöcke breit, die Karte reicht von -{@link #HALF} bis +{@link #HALF}
 * (Bild-Beschriftung "225k" = 225000 Blöcke). Falls die Skala bei dir anders ist, reichen diese
 * beiden Konstanten - alles andere (Karte im Browser, Zielsuche im RtpFinder) rechnet damit.
 *
 * Reihen laufen von Nord (Z = -HALF) nach Süd (Z = +HALF), Spalten von West (X = -HALF) nach Ost.
 * Es gibt keine Lücken: jede Koordinate gehört zu genau einem Feld (außerhalb wird aufs Randfeld geklemmt).
 */
public final class RtpRegionGrid {

    public static final int CELL = 50_000;
    public static final int N    = 9;
    public static final int HALF = CELL * N / 2; // 225000

    /** token = exaktes Argument von /rtp (muss zu {@link RtpFinder#REGIONS} passen). */
    public record MapRegion(String token, String name, String color) {}

    // Reihenfolge = Index im Raster (W, E, A, O, C, G)
    public static final List<MapRegion> REGIONS = List.of(
            new MapRegion("west",       "NA West",    "#3F6DB5"), // 0  W
            new MapRegion("east",       "NA East",    "#5AAEE8"), // 1  E
            new MapRegion("asia",       "Asia",       "#F0C24B"), // 2  A
            new MapRegion("oceania",    "Oceania",    "#F28C28"), // 3  O
            new MapRegion("eu_central", "EU Central", "#9ED36A"), // 4  C
            new MapRegion("eu_west",    "EU West",    "#1FA56B")  // 5  G
    );

    private static final String LETTERS = "WEAOCG";

    /** Region pro Feld, Zeile für Zeile von Nord nach Süd. */
    private static final String[] ROWS = {
            "OWWWEEEEE",
            "OWWWEEEEE",
            "OWWWEEEEE",
            "OOOWEEEEE",
            "AAAAEEEEE",
            "AGGCCCCCE",
            "AGGCCCCCC",
            "CGCCCCCCC",
            "CGGGGGGGC"
    };

    /** Feldnummern aus dem Bild (109-111 waren vom Bild-Overlay verdeckt, sind aus der Reihenfolge ergänzt). */
    private static final int[][] IDS = {
            { 82, 100, 101, 102, 103, 104, 105, 106,  91},
            { 83,  44,  75,  42,  41,  40,  39,  38,  92},
            { 84,  45,  14,  13,  12,  11,  10,  37,  93},
            { 85,  46,  74,   3,   2,   1,  25,  36,  94},
            { 86,  47,  72,  71,   5,   4,  24,  35,  95},
            { 87,  51,  17,   9,   8,   7,  23,  34,  96},
            { 88,  54,  18,  61,  62,  21,  22,  33,  97},
            { 89,  26,  27,  28,  29,  30,  59,  32,  98},
            { 90, 107, 108, 109, 110, 111, 112, 113,  99}
    };

    private static final int[][] GRID = new int[N][N];
    private static final double[] REGION_AREA = new double[REGIONS.size()];

    static {
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                int idx = LETTERS.indexOf(ROWS[z].charAt(x));
                GRID[z][x] = idx;
                REGION_AREA[idx] += (double) CELL * CELL;
            }
        }
    }

    private RtpRegionGrid() {}

    // ─── Lookup ───────────────────────────────────────────────────────────────

    private static int cellIndex(double v) {
        int i = (int) Math.floor((v + HALF) / CELL);
        return Math.max(0, Math.min(N - 1, i));
    }

    public static MapRegion regionAt(double x, double z) {
        return REGIONS.get(GRID[cellIndex(z)][cellIndex(x)]);
    }

    public static boolean inWorld(double x, double z) {
        return x >= -HALF && x <= HALF && z >= -HALF && z <= HALF;
    }

    // ─── Kreis-Überlappung ────────────────────────────────────────────────────

    /**
     * @param region         Region
     * @param share          Anteil der Kreisfläche (0..1), der in dieser Region liegt
     * @param hitChance      Chance pro /rtp in diese Region, im Kreis zu landen (gleichverteilt angenommen)
     */
    public record Overlap(MapRegion region, double share, double hitChance) {}

    /** Regionen, die der Kreis berührt - beste Trefferchance zuerst. */
    public static List<Overlap> overlaps(double cx, double cz, double r) {
        final int n = 64;
        int[] counts = new int[REGIONS.size()];
        int total = 0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double px = cx - r + (i + 0.5) / n * 2 * r;
                double pz = cz - r + (j + 0.5) / n * 2 * r;
                double dx = px - cx, dz = pz - cz;
                if (dx * dx + dz * dz > r * r) continue;
                if (!inWorld(px, pz)) continue;
                counts[GRID[cellIndex(pz)][cellIndex(px)]]++;
                total++;
            }
        }

        double sampleArea = Math.pow(2 * r / n, 2);
        List<Overlap> out = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            out.add(new Overlap(REGIONS.get(i),
                    total == 0 ? 0 : (double) counts[i] / total,
                    counts[i] * sampleArea / REGION_AREA[i]));
        }
        out.sort((a, b) -> Double.compare(b.hitChance(), a.hitChance()));
        return out;
    }

    // ─── JSON für die HTML-Karte ──────────────────────────────────────────────

    public static String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"cell\":").append(CELL).append(",\"half\":").append(HALF).append(",\"regions\":[");
        for (int i = 0; i < REGIONS.size(); i++) {
            MapRegion r = REGIONS.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"token\":\"").append(r.token()).append("\",\"name\":\"").append(r.name())
                    .append("\",\"color\":\"").append(r.color()).append("\"}");
        }
        sb.append("],\"rows\":[");
        for (int z = 0; z < N; z++) {
            if (z > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < N; x++) sb.append(x > 0 ? "," : "").append(GRID[z][x]);
            sb.append("]");
        }
        sb.append("],\"ids\":[");
        for (int z = 0; z < N; z++) {
            if (z > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < N; x++) sb.append(x > 0 ? "," : "").append(IDS[z][x]);
            sb.append("]");
        }
        return sb.append("]}").toString();
    }
}