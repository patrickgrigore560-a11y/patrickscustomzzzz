package ro.patrickszcustoms.animation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import ro.patrickszcustoms.PatrickszCustomsClient;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;

/**
 * Transition system for 1.21.11 (GUI = deferred GuiRenderState, Matrix3x2fStack).
 *
 * Slide / zoom: 2D matrix transform around the whole screen render.
 * Fade: 1.21.6+ has no global shader color anymore, so DrawContextMixin multiplies
 * the ARGB alpha of fills / textured quads / text by {@link #alpha} while a transition runs.
 * (Item icons and widget labels drawn through the text consumer are not faded.)
 *
 * CLOSE: the replaced screen is kept as a "ghost" and rendered for a few ms with the
 * reverse transform (under the next screen, or over the HUD if none follows).
 */
public final class ScreenAnimator {
    private static final float SLIDE_FRACTION = 0.25f;

    private static Screen ghost;
    private static long ghostStart;
    private static Screen opening;
    private static long openStart;
    private static boolean pushed;
    private static float lastDelta;

    /** Current global alpha multiplier (1 = untouched). Read by DrawContextMixin. */
    private static float alpha = 1f;

    private ScreenAnimator() {}

    public static float getAlpha() { return alpha; }
    public static void setAlpha(float a) { alpha = MathHelper.clamp(a, 0f, 1f); }

    /** Multiplies the alpha channel of an ARGB color by the current transition alpha. */
    public static int modulate(int argb) {
        if (alpha >= 0.999f) return argb;
        int a = argb >>> 24;
        if (a == 0) return argb;
        return (Math.round(a * alpha) << 24) | (argb & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ easing
    public static float easeOut(float p) { float i = 1f - p; return 1f - i * i * i; }
    public static float easeIn(float p) { return p * p * p; }

    /** Screen transitions use the easing chosen by the player; UI internals keep the fixed cubic curves. */
    public static float easeOpen(float p) { return ConfigManager.get().easing.out(p); }
    public static float easeClose(float p) { return ConfigManager.get().easing.in(p); }

    // ------------------------------------------------------------ screen change
    public static void onScreenChange(Screen old, Screen next) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled || old == next) return;
        long now = Util.getMeasuringTimeMs();
        if (old != null && eligible(old)) { ghost = old; ghostStart = now; }
        if (next != null && eligible(next)) {
            opening = next;
            openStart = now;
            if (ghost == next) ghost = null;
        }
    }

    private static boolean eligible(Screen s) {
        if (s instanceof ChatScreen) return false;
        String n = s.getClass().getSimpleName();
        return !(n.contains("Loading") || n.contains("Progress") || n.contains("Connect")
                || n.contains("Message") || n.contains("Downloading")
                || n.contains("Reconfiguring") || n.contains("Overlay"));
    }

    // ----------------------------------------------------------- render hooks
    /** HEAD of Screen#renderWithTooltip. */
    public static void begin(DrawContext ctx, Screen screen, float delta) {
        pushed = false;
        lastDelta = delta;
        ModConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled) return;
        long now = Util.getMeasuringTimeMs();

        if (ghost != null && ghost != screen) drawGhost(ctx, cfg, now);

        if (screen == opening) {
            float p = (now - openStart) / (float) cfg.animationDurationMs;
            if (p < 1f) {
                ctx.getMatrices().pushMatrix();
                applyTransform(ctx, easeOpen(MathHelper.clamp(p, 0f, 1f)),
                        cfg.animationType, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
                pushed = true;
            }
        }
    }

    /** RETURN of Screen#renderWithTooltip. */
    public static void end(DrawContext ctx) {
        if (!pushed) return;
        ctx.getMatrices().popMatrix();
        alpha = 1f;
        pushed = false;
    }

    public static void renderGhostOnHud(DrawContext ctx) {
        if (ghost == null || MinecraftClient.getInstance().currentScreen != null) return;
        drawGhost(ctx, ConfigManager.get(), Util.getMeasuringTimeMs());
    }

    private static void drawGhost(DrawContext ctx, ModConfig cfg, long now) {
        float p = (now - ghostStart) / (float) cfg.animationDurationMs;
        if (p >= 1f || !cfg.animationsEnabled) { ghost = null; return; }
        float v = 1f - easeClose(MathHelper.clamp(p, 0f, 1f));
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        try {
            applyTransform(ctx, v, cfg.animationType,
                    ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
            ghost.render(ctx, -1000, -1000, lastDelta); // far mouse: no hover effects
        } catch (Throwable t) {
            PatrickszCustomsClient.LOGGER.warn("Closing animation failed for {}, dropping ghost", ghost, t);
            ghost = null;
        } finally {
            m.popMatrix();
            alpha = 1f;
        }
    }

    // ------------------------------------------------------------- transform
    /**
     * @param v visibility 0 (hidden) .. 1 (shown). Sets the global alpha to v.
     * Also used by the config GUI preview.
     */
    public static void applyTransform(DrawContext ctx, float v, AnimationType type, int w, int h) {
        Matrix3x2fStack m = ctx.getMatrices();
        float inv = 1f - v;
        switch (type) {
            case FADE -> { }
            case SLIDE_LEFT -> m.translate(-inv * w * SLIDE_FRACTION, 0f);
            case SLIDE_RIGHT -> m.translate(inv * w * SLIDE_FRACTION, 0f);
            case SLIDE_UP -> m.translate(0f, -inv * h * SLIDE_FRACTION);
            case SLIDE_DOWN -> m.translate(0f, inv * h * SLIDE_FRACTION);
            case ZOOM_IN -> zoom(m, 0.8f + 0.2f * v, w, h);
            case ZOOM_OUT -> zoom(m, 1.2f - 0.2f * v, w, h);
        }
        setAlpha(v);
    }

    private static void zoom(Matrix3x2fStack m, float s, int w, int h) {
        m.translate(w / 2f, h / 2f);
        m.scale(s, s);
        m.translate(-w / 2f, -h / 2f);
    }
}
