package ro.patrickszcustoms.spotify;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import ro.patrickszcustoms.animation.ScreenAnimator;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.gui.Ui;

/** In-game "now playing" pill with animated equalizer. Also used by the menu preview. */
public final class SpotifyHud {
    private static String lastText = "";
    private static long changeMs = 0L;

    private SpotifyHud() {}

    /** HUD element: only draws when enabled, no screen is open and the HUD is visible. */
    public static void render(DrawContext ctx) {
        ModConfig c = ConfigManager.get();
        if (!c.spotifyHudEnabled && !c.lyricsEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.options.hudHidden) return;

        SpotifyController.pollIfDue(c.lyricsEnabled ? 1000L : 5000L);
        SpotifyController.Status st = SpotifyController.getStatus();
        if (c.lyricsEnabled) LyricsHud.render(ctx, st);
        if (!c.spotifyHudEnabled) return;
        boolean playing = st.state() == SpotifyController.State.PLAYING;
        boolean paused = st.state() == SpotifyController.State.PAUSED;
        if (!playing && !(paused && !c.spotifyHudOnlyPlaying)) return;

        String text = playing ? st.track()
                : Tr.t("patrickszcustoms.spotify.paused_short").getString();
        if (!text.equals(lastText)) { lastText = text; changeMs = Util.getMeasuringTimeMs(); }
        drawPill(ctx, 0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), text, playing, changeMs);
    }

    /**
     * Draws the pill inside the area (ax, ay, aw, ah): whole screen for the real HUD,
     * the preview box for the menu.
     */
    public static void drawPill(DrawContext ctx, int ax, int ay, int aw, int ah,
                                String text, boolean playing, long trackChangeMs) {
        ModConfig c = ConfigManager.get();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        long now = Util.getMeasuringTimeMs();

        String shown = text;
        if (tr.getWidth(shown) > 170) shown = tr.trimToWidth(shown, 164) + "...";
        int tw = tr.getWidth(shown);
        int barsW = 12, padX = 6, h = 18;
        int w = padX + barsW + 5 + tw + padX;
        float s = c.spotifyHudScale;
        float sw = w * s, sh = h * s, m = 6f;

        float x, y;
        switch (c.spotifyHudPos) {
            case TOP_LEFT -> { x = ax + m; y = ay + m; }
            case TOP_CENTER -> { x = ax + (aw - sw) / 2f; y = ay + m; }
            case BOTTOM_LEFT -> { x = ax + m; y = ay + ah - sh - m; }
            case BOTTOM_RIGHT -> { x = ax + aw - sw - m; y = ay + ah - sh - m; }
            default -> { x = ax + aw - sw - m; y = ay + m; }
        }

        x += c.hudOffsetX;
        y += c.hudOffsetY;

        // slide + fade-in whenever the track changes
        float p = ScreenAnimator.easeOut(MathHelper.clamp((now - trackChangeMs) / 450f, 0f, 1f));
        boolean top = c.spotifyHudPos.name().startsWith("TOP");
        y += (1f - p) * (top ? -14f : 14f);
        float a = c.spotifyHudAlpha * p;

        int acc = Ui.accent() | 0xFF000000;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(s, s);

        Ui.rect(ctx, 0, 0, w, h, Ui.alpha(0xFF0C0C14, a * 0.88f));
        ctx.fill(0, 2, 2, h - 2, Ui.alpha(acc, a));

        // equalizer bars
        for (int i = 0; i < 4; i++) {
            int bh = playing
                    ? 3 + (int) Math.round(Math.abs(Math.sin(now / 130.0 + i * 1.7)) * 9)
                    : 3;
            int bx = padX + i * 3;
            ctx.fill(bx, h - 4 - bh, bx + 2, h - 4, Ui.alpha(acc, a));
        }
        ctx.drawTextWithShadow(tr, shown, padX + barsW + 5, (h - 8) / 2, Ui.alpha(0xFFFFFFFF, a));

        ctx.getMatrices().popMatrix();
    }
}
