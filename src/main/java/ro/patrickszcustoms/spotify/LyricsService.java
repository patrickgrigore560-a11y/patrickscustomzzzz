package ro.patrickszcustoms.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches time-synced lyrics (LRC) from lrclib.net on a background thread and caches them per song.
 * Only the artist and title of the current song are sent. Nothing is stored on disk.
 */
public final class LyricsService {
    public enum LoadState { LOADING, FOUND, NONE }

    public record Line(long ms, String text) {}

    public record Result(LoadState state, List<Line> lines) {}

    private static final Result LOADING = new Result(LoadState.LOADING, null);
    private static final Result NONE = new Result(LoadState.NONE, null);
    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "patrickszcustoms-lyrics");
        t.setDaemon(true);
        return t;
    });

    private LyricsService() {}

    /** Returns immediately: LOADING while the request runs, then FOUND / NONE (cached). */
    public static Result get(String artist, String title, long durationMs) {
        if (title == null || title.isBlank()) return NONE;
        String key = (artist + "|" + title).toLowerCase();
        Result r = CACHE.get(key);
        if (r != null) return r;
        if (CACHE.size() > 60) CACHE.clear();
        CACHE.put(key, LOADING);
        EXEC.submit(() -> CACHE.put(key, fetch(artist, title, durationMs)));
        return LOADING;
    }

    /** Index of the line active at posMs, or -1 before the first line. */
    public static int indexAt(List<Line> lines, long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).ms() <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    // ------------------------------------------------------------------ fetch
    private static Result fetch(String artist, String title, long durationMs) {
        try {
            String a = artist == null ? "" : artist.split("\\s*(,|;|&| feat\\.? )\\s*")[0].trim();
            List<Line> lines = search(a, title, durationMs);
            if (lines == null) {
                String cleaned = title.replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("\\s+-\\s+.*$", "").trim();
                if (!cleaned.isEmpty() && !cleaned.equals(title)) lines = search(a, cleaned, durationMs);
            }
            return lines == null || lines.isEmpty() ? NONE : new Result(LoadState.FOUND, lines);
        } catch (Exception e) {
            return NONE;
        }
    }

    private static List<Line> search(String artist, String title, long durationMs) throws Exception {
        String url = "https://lrclib.net/api/search?track_name=" + enc(title)
                + (artist.isEmpty() ? "" : "&artist_name=" + enc(artist));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "patrickszcustoms (Minecraft Fabric mod)")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) return null;

        JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
        JsonObject best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            JsonElement synced = o.get("syncedLyrics");
            if (synced == null || synced.isJsonNull() || synced.getAsString().isBlank()) continue;
            double diff = 0;
            if (durationMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                diff = Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0);
                if (diff > 6.0) continue; // different version of the song
            }
            if (diff < bestDiff) { bestDiff = diff; best = o; }
        }
        return best == null ? null : parseLrc(best.get("syncedLyrics").getAsString());
    }

    /** "[mm:ss.xx] text" lines, multiple tags per line allowed. */
    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long ms = (long) (Integer.parseInt(m.group(1)) * 60_000L + Double.parseDouble(m.group(2)) * 1000.0);
                times.add(ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return out;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
