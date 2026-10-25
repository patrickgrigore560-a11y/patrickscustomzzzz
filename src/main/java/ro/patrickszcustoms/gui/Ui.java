package ro.patrickszcustoms.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;

/** Shared look & feel: colors, rounded rects, animated background. */
public final class Ui {
    /** Set by ConfigScreen every frame: section-switch animation progress (0..1) and direction. */
    public static float contentT = 1f;
    public static int contentDir = 1;
    public static long screenOpenMs = 0L;

    private Ui() {}

    public static int accent() {
        ModConfig c = ConfigManager.get();
        if (c.rainbowAccent) return 0xFF000000 | hsv((Util.getMeasuringTimeMs() % 8000L) / 8000f, 0.65f, 1f);
        return c.theme.accent;
    }

    public static int hsv(float h, float s, float v) {
        int i = (int) (h * 6f);
        float f = h * 6f - i, p = v * (1f - s), q = v * (1f - f * s), t = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static int mix(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = Math.round(MathHelper.lerp(t, a >>> 24, b >>> 24));
        int ar = Math.round(MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int ag = Math.round(MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int ab = Math.round(MathHelper.lerp(t, a & 0xFF, b & 0xFF));
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Multiplies the alpha channel of an ARGB color. */
    public static int alpha(int argb, float m) {
        int a = Math.round((argb >>> 24) * MathHelper.clamp(m, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Rect with cut corners (cheap "rounded" look). */
    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + w, y + h - 1, color);
    }

    /** Animated gradient + drifting particles, tinted by the current theme. */
    public static void drawBackground(DrawContext ctx, int w, int h) {
        ModConfig c = ConfigManager.get();
        long now = Util.getMeasuringTimeMs();
        float t = now / 1000f;
        float k = 0.5f + 0.5f * (float) Math.sin(t * 0.35f);
        int top = mix(c.theme.top, c.theme.bottom, k);
        int bottom = mix(c.theme.bottom, c.theme.top, k);
        if (c.rainbowAccent) { // tint the whole background with the moving hue
            int tint = 0xFF000000 | hsv((now % 8000L) / 8000f, 0.7f, 0.35f);
            top = mix(top, tint, 0.45f);
            bottom = mix(bottom, tint, 0.25f);
        }
        ctx.fillGradient(0, 0, w, h, top, bottom);

        int acc = accent();
        for (int i = 0; i < 22; i++) {
            float speed = 10f + (i % 5) * 7f;
            float y = h - ((t * speed + i * 53f) % (h + 24f)) + 12f;
            float x = ((i * 79) % Math.max(1, w)) + (float) Math.sin(t * 0.5f + i) * 14f;
            int size = 1 + (i % 3);
            float a = 0.18f + 0.22f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f + i * 2f));
            ctx.fill((int) x, (int) y, (int) x + size, (int) y + size, alpha(acc | 0xFF000000, a));
        }
    }
}
