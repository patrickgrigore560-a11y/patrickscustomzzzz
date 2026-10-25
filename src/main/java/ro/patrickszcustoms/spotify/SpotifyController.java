package ro.patrickszcustoms.spotify;

import ro.patrickszcustoms.PatrickszCustomsClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Controls and reads a locally running Spotify desktop app. No API key / login.
 *
 *  Windows: previous/next/play-pause via media keys; track + position via the Windows media session
 *           (one long-lived PowerShell process that prints a line per second while the mod needs it).
 *           Falls back to the Spotify window title if the media session is unavailable.
 *  macOS  : AppleScript (osascript), first use asks for "Automation" permission.
 *  Linux  : playerctl (MPRIS player "spotify").
 *
 * Only fixed, hard-coded commands are executed (no user input is ever put into a command).
 */
public final class SpotifyController {
    public enum Action { PREVIOUS, PLAY_PAUSE, NEXT }
    public enum State { UNKNOWN, NOT_RUNNING, RUNNING_NO_INFO, PAUSED, PLAYING }

    /** positionMs / durationMs are -1 when unknown. stampMs = when positionMs was measured. */
    public record Status(State state, String track, String artist, String title,
                         long positionMs, long durationMs, long stampMs) {
        public Status(State state, String track) {
            this(state, track, "", "", -1L, -1L, System.currentTimeMillis());
        }
    }

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "patrickszcustoms-spotify");
        t.setDaemon(true);
        return t;
    });

    private static volatile Status status = new Status(State.UNKNOWN, "");
    private static volatile boolean polling;
    private static long lastPoll;

    // Windows bridge
    private static Process bridge;
    private static volatile boolean bridgeOk, bridgeBroken;
    private static volatile long lastTouch;
    private static boolean hookAdded;

    private static final String PS_SCRIPT = """
            $ErrorActionPreference='Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
            $asTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0]
            function Await($op,$t){$m=$asTask.MakeGenericMethod($t);$k=$m.Invoke($null,@($op));[void]$k.Wait(-1);$k.Result}
            [Console]::OutputEncoding=(New-Object System.Text.UTF8Encoding $false)
            $mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            while($true){
              try{
                $s=$mgr.GetSessions()|Where-Object{$_.SourceAppUserModelId -like '*Spotify*'}|Select-Object -First 1
                if(-not $s){'NOTRUNNING'}else{
                  $pr=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $tl=$s.GetTimelineProperties();$pb=$s.GetPlaybackInfo()
                  $pos=$tl.Position.TotalMilliseconds
                  $st=[string]$pb.PlaybackStatus
                  if($st -eq 'Playing'){$pos+=([DateTimeOffset]::UtcNow-$tl.LastUpdatedTime).TotalMilliseconds}
                  ('{0}|{1}|{2}|{3}|{4}' -f $st,[int64]$pos,[int64]$tl.EndTime.TotalMilliseconds,($pr.Artist -replace '\\|',' '),($pr.Title -replace '\\|',' '))
                }
              }catch{'ERROR'}
              [Console]::Out.Flush()
              Start-Sleep -Milliseconds 1000
            }
            """;

    private SpotifyController() {}

    public static Status getStatus() { return status; }

    /** Current playback position in ms (extrapolated while playing), or -1 if unknown. */
    public static long positionMs() {
        Status s = status;
        if (s.positionMs() < 0L) return -1L;
        long p = s.positionMs();
        if (s.state() == State.PLAYING) p += System.currentTimeMillis() - s.stampMs();
        if (s.durationMs() > 0L) p = Math.min(p, s.durationMs());
        return Math.max(0L, p);
    }

    public static void pollIfDue() { pollIfDue(2000L); }

    /** Call from the render thread; refreshes at most every intervalMs. Also keeps the Windows bridge alive. */
    public static void pollIfDue(long intervalMs) {
        long now = System.currentTimeMillis();
        lastTouch = now;
        if (polling || now - lastPoll < intervalMs) return;
        lastPoll = now;
        polling = true;
        EXEC.submit(() -> {
            try {
                Status s = query();
                if (s != null) status = s;
            } catch (Exception e) {
                PatrickszCustomsClient.LOGGER.debug("Spotify query failed", e);
            } finally {
                polling = false;
            }
        });
    }

    /** Sends previous / play-pause / next. Ignored when Spotify is known not to be running. */
    public static void send(Action action) {
        if (status.state() == State.NOT_RUNNING) return;
        EXEC.submit(() -> {
            try {
                run(commandFor(action));
                Thread.sleep(500);
                Status s = query();
                if (s != null) status = s;
            } catch (Exception e) {
                PatrickszCustomsClient.LOGGER.warn("Spotify command failed", e);
            }
        });
    }

    // ------------------------------------------------------------- commands
    private static List<String> commandFor(Action a) {
        if (WINDOWS) {
            int code = switch (a) { case NEXT -> 176; case PREVIOUS -> 177; case PLAY_PAUSE -> 179; };
            return List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object -ComObject WScript.Shell).SendKeys([char]" + code + ")");
        }
        if (MAC) {
            String verb = switch (a) { case NEXT -> "next track"; case PREVIOUS -> "previous track"; case PLAY_PAUSE -> "playpause"; };
            return List.of("osascript", "-e", "tell application \"Spotify\" to " + verb);
        }
        String verb = switch (a) { case NEXT -> "next"; case PREVIOUS -> "previous"; case PLAY_PAUSE -> "play-pause"; };
        return List.of("playerctl", "-p", "spotify", verb);
    }

    private static Status query() {
        if (WINDOWS) {
            ensureBridge();
            if (!bridgeBroken) return null; // the bridge thread updates `status` itself
            return queryWindowTitle();
        }
        if (MAC) {
            String out = run(List.of("osascript",
                    "-e", "if application \"Spotify\" is running then",
                    "-e", "tell application \"Spotify\"",
                    "-e", "set st to player state as string",
                    "-e", "set ps to ((player position) * 1000) as integer",
                    "-e", "set du to (duration of current track) as integer",
                    "-e", "return st & \"|\" & ps & \"|\" & du & \"|\" & (artist of current track) & \"|\" & (name of current track)",
                    "-e", "end tell",
                    "-e", "else", "-e", "return \"NOTRUNNING\"", "-e", "end if"));
            return parseLine(out, false);
        }
        String out = run(List.of("playerctl", "-p", "spotify", "metadata", "--format",
                "{{status}}|{{position}}|{{mpris:length}}|{{artist}}|{{title}}"));
        return parseLine(out, true); // playerctl reports microseconds
    }

    /** Fallback: Spotify window title is "Artist - Song" while playing. */
    private static Status queryWindowTitle() {
        String out = run(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "$p=Get-Process Spotify -ErrorAction SilentlyContinue; "
                        + "if(-not $p){'NOTRUNNING'} else { "
                        + "$t=($p | Where-Object {$_.MainWindowTitle} | Select-Object -First 1 -ExpandProperty MainWindowTitle); "
                        + "if($t){$t}else{'NOINFO'} }"));
        if (out == null) return new Status(State.UNKNOWN, "");
        out = out.trim();
        if (out.equals("NOTRUNNING")) return new Status(State.NOT_RUNNING, "");
        if (out.equals("NOINFO")) return new Status(State.RUNNING_NO_INFO, "");
        if (out.equals("Spotify") || out.equals("Spotify Free") || out.equals("Spotify Premium"))
            return new Status(State.PAUSED, "");
        int dash = out.indexOf(" - ");
        String artist = dash > 0 ? out.substring(0, dash) : "";
        String title = dash > 0 ? out.substring(dash + 3) : out;
        return new Status(State.PLAYING, out, artist, title, -1L, -1L, System.currentTimeMillis());
    }

    /**
     * "state|position|duration|artist|title" (position / duration in ms, or microseconds if micro),
     * "NOTRUNNING", or null for unusable lines.
     */
    private static Status parseLine(String out, boolean micro) {
        if (out == null) return new Status(State.RUNNING_NO_INFO, "");
        out = out.replace("\uFEFF", "").trim();
        if (out.equals("ERROR")) return null;
        if (out.isEmpty() || out.equals("NOTRUNNING") || out.toLowerCase(Locale.ROOT).contains("no players"))
            return new Status(State.NOT_RUNNING, "");
        String[] p = out.split("\\|", 5);
        if (p.length < 5) return new Status(State.RUNNING_NO_INFO, "");
        State st = p[0].trim().toLowerCase(Locale.ROOT).contains("play") ? State.PLAYING : State.PAUSED;
        String artist = p[3].trim(), title = p[4].trim();
        String track = artist.isEmpty() ? title : artist + " - " + title;
        return new Status(st, track, artist, title, parseMs(p[1], micro), parseMs(p[2], micro), System.currentTimeMillis());
    }

    private static long parseMs(String s, boolean micro) {
        try {
            long v = (long) Double.parseDouble(s.trim());
            return v < 0 ? -1L : (micro ? v / 1000L : v);
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    // -------------------------------------------------------- Windows bridge
    private static synchronized void ensureBridge() {
        if (!WINDOWS || bridgeBroken) return;
        if (bridge != null && bridge.isAlive()) return;
        try {
            Path f = Files.createTempFile("patrickszcustoms-spotify", ".ps1");
            Files.writeString(f, PS_SCRIPT, StandardCharsets.UTF_8);
            f.toFile().deleteOnExit();
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", f.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            bridge = p;
            if (!hookAdded) {
                hookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (bridge != null) bridge.destroyForcibly(); }));
            }
            Thread t = new Thread(() -> readBridge(p), "patrickszcustoms-spotify-bridge");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            bridgeBroken = true;
        }
    }

    private static void readBridge(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (System.currentTimeMillis() - lastTouch > 20000L) break; // nobody needs us: stop
                Status s = parseLine(line, false);
                if (s != null) { bridgeOk = true; status = s; }
            }
        } catch (Exception ignored) {
            // process ended
        } finally {
            p.destroyForcibly();
            if (!bridgeOk) bridgeBroken = true; // never produced a valid line -> use the window-title fallback
        }
    }

    /** Runs a command, returns its output, or null when it could not be started / timed out. */
    private static String run(List<String> cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0 && !WINDOWS) return sb.length() == 0 ? "NOTRUNNING" : sb.toString();
            return sb.toString();
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return null;
        }
    }
}
