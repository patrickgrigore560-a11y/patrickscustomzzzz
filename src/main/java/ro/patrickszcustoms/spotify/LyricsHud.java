package ro.patrickszcustoms.spotify;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import ro.patrickszcustoms.animation.ScreenAnimator;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.gui.Ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Synced-lyrics bar: [clock] ( equalizer | current lyric with karaoke word highlight | ... ) [song time].
 * The line slides up when it changes and the bar smoothly resizes to the text.
 */
public final class LyricsHud {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static String lastLine = "";
    private static String prevLine = "";
    private static long changeMs = 0L;
    private static float barW = 0f;

    private LyricsHud() {}

    /** Real in-game bar. Called from SpotifyHud only when lyrics are enabled. */
    public static void render(DrawContext ctx, SpotifyController.Status st) {
        if (st.state() != SpotifyController.State.PLAYING && st.state() != SpotifyController.State.PAUSED) return;
        boolean playing = st.state() == SpotifyController.State.PLAYING;
        String title = st.title().isEmpty() ? st.track() : st.title();
        if (title.isEmpty()) return;

        long pos = SpotifyController.positionMs();
        LyricsService.Result r = LyricsService.get(st.artist(), title, st.durationMs());

        String line = st.track().isEmpty() ? title : st.track(); // fallback: "Artist - Title"
        float prog = 1f;
        if (r.state() == LyricsService.LoadState.FOUND && pos >= 0L) {
            List<LyricsService.Line> lines = r.lines();
            int i = LyricsService.indexAt(lines, pos);
            if (i >= 0) {
                LyricsService.Line l = lines.get(i);
                long end = i + 1 < lines.size() ? lines.get(i + 1).ms() : l.ms() + 5000L;
                long sing = Math.min(Math.max(1L, end - l.ms()), Math.max(1200L, l.text().length() * 85L));
                prog = MathHelper.clamp((pos - l.ms()) / (float) sing, 0f, 1f);
                line = l.text().isEmpty() ? "..." : l.text();
            }
        }
        if (!line.equals(lastLine)) { prevLine = lastLine; lastLine = line; changeMs = Util.getMeasuringTimeMs(); }

        MinecraftClient mc = MinecraftClient.getInstance();
        drawBar(ctx, 0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                line, prevLine, prog, changeMs, playing, pos);
    }

    /**
     * Draws the bar inside the area (ax, ay, aw, ah): whole screen for the real HUD, the preview box in the menu.
     *
     * @param elapsedMs song position for the right-hand timer, or -1 to hide it
     */
    public static void drawBar(DrawContext ctx, int ax, int ay, int aw, int ah,
                               String line, String previous, float prog, long lineChangeMs,
                               boolean playing, long elapsedMs) {
        ModConfig c = ConfigManager.get();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        long now = Util.getMeasuringTimeMs();
        int acc = Ui.accent() | 0xFF000000;

        String text = fit(tr, line, 230);
        int textW = tr.getWidth(text);
        float target = Math.max(150f, textW + 46f);
        if (barW <= 0f) barW = target;
        barW += (target - barW) * 0.25f; // pill morphs smoothly between lines
        int w = Math.round(barW), h = 18;

        String clock = c.lyricsClock ? LocalTime.now().format(CLOCK) : null;
        int clockW = clock == null ? 0 : tr.getWidth(clock) + 8;
        String time = c.lyricsElapsed && elapsedMs >= 0L ? fmt(elapsedMs) : null;
        int timeW = time == null ? 0 : tr.getWidth(time) + 8;

        float s = c.lyricsScale;
        float gw = (clockW + w + timeW) * s, gh = h * s, m = 6f;
        float x, y;
        switch (c.lyricsPos) {
            case TOP_LEFT -> { x = ax + m; y = ay + m; }
            case TOP_CENTER -> { x = ax + (aw - gw) / 2f; y = ay + m; }
            case BOTTOM_LEFT -> { x = ax + m; y = ay + ah - gh - m; }
            case BOTTOM_RIGHT -> { x = ax + aw - gw - m; y = ay + ah - gh - m; }
            default -> { x = ax + aw - gw - m; y = ay + m; }
        }
        x += c.lyricsOffsetX;
        y += c.lyricsOffsetY;

        int baseY = (h - 8) / 2;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(s, s);

        if (clock != null) ctx.drawTextWithShadow(tr, clock, 0, baseY, 0xFFB8BCD0);

        int px = clockW;
        Ui.rect(ctx, px, 0, w, h, 0xF00A0A12);
        ctx.drawStrokedRectangle(px, 0, w, h, Ui.alpha(acc, 0.40f));

        // equalizer icon
        for (int i = 0; i < 3; i++) {
            int bh = playing ? 2 + (int) Math.round(Math.abs(Math.sin(now / 140.0 + i * 1.9)) * 7) : 2;
            ctx.fill(px + 7 + i * 3, h - 4 - bh, px + 9 + i * 3, h - 4, acc);
        }

        // lyric text, clipped to the pill; old line slides up, new line slides in from below
        ctx.enableScissor(px + 20, 0, px + w - 20, h);
        float p = ScreenAnimator.easeOut(MathHelper.clamp((now - lineChangeMs) / 240f, 0f, 1f));
        if (previous != null && !previous.isEmpty() && p < 1f) {
            String pv = fit(tr, previous, 230);
            ctx.drawTextWithShadow(tr, pv, px + (w - tr.getWidth(pv)) / 2, baseY - Math.round(p * 10f),
                    Ui.alpha(0xFF8E90A8, 1f - p));
        }
        karaoke(ctx, tr, text, px + (w - textW) / 2, baseY + Math.round((1f - p) * 10f), prog, acc, p, c.lyricsChip);
        ctx.disableScissor();

        for (int i = 0; i < 3; i++) { // the "..." menu dots
            ctx.fill(px + w - 14 + i * 4, h / 2 - 1, px + w - 12 + i * 4, h / 2 + 1, Ui.alpha(0xFFFFFFFF, 0.55f));
        }
        if (time != null) ctx.drawTextWithShadow(tr, time, px + w + 8, baseY, 0xFFB8BCD0);

        ctx.getMatrices().popMatrix();
    }

    /** Sung words white, the current word on an accent chip, upcoming words dim. */
    private static void karaoke(DrawContext ctx, TextRenderer tr, String text, int x, int y,
                                float progress, int acc, float alpha, boolean chip) {
        float cursor = progress * text.length();
        int idx = 0, cx = x, space = tr.getWidth(" ");
        for (String word : text.split(" ", -1)) {
            int start = idx, end = idx + word.length();
            idx = end + 1;
            int ww = tr.getWidth(word);
            boolean sung = end <= cursor;
            boolean current = !sung && start <= cursor;
            if (current && chip) Ui.rect(ctx, cx - 2, y - 2, ww + 4, 11, Ui.alpha(acc, 0.85f * alpha));
            int color = sung || current ? 0xFFFFFFFF : 0xFF8E90A8;
            ctx.drawTextWithShadow(tr, word, cx, y, Ui.alpha(color, alpha));
            cx += ww + space;
        }
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (s == null || s.isEmpty()) return "...";
        return tr.getWidth(s) > maxW ? tr.trimToWidth(s, maxW - 8) + "..." : s;
    }

    private static String fmt(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        return (sec / 60L) + ":" + String.format("%02d", sec % 60L);
    }
}
