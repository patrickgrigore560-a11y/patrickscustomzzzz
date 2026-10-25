package ro.patrickszcustoms.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import ro.patrickszcustoms.config.CrosshairType;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.gui.Ui;

import java.util.Arrays;

/**
 * All crosshair shapes. Shapes are collected as rectangles first, then drawn in two passes
 * (outline for every rect, then fills) so overlapping outlines never cover neighbouring parts.
 */
public final class CrosshairRenderer {
    private static final Identifier VANILLA = Identifier.ofVanilla("hud/crosshair");

    private static int[] rects = new int[256];
    private static int n;

    private CrosshairRenderer() {}

    // ----------------------------------------------------------------- batch
    private static void add(int x0, int y0, int x1, int y1) {
        if (n + 4 > rects.length) rects = Arrays.copyOf(rects, rects.length * 2);
        rects[n++] = x0; rects[n++] = y0; rects[n++] = x1; rects[n++] = y1;
    }

    private static void flush(DrawContext ctx, int color, int outlineColor, boolean outline) {
        if (outline && (outlineColor >>> 24) != 0) {
            for (int i = 0; i < n; i += 4) ctx.fill(rects[i] - 1, rects[i + 1] - 1, rects[i + 2] + 1, rects[i + 3] + 1, outlineColor);
        }
        for (int i = 0; i < n; i += 4) ctx.fill(rects[i], rects[i + 1], rects[i + 2], rects[i + 3], color);
        n = 0;
    }

    // ---------------------------------------------------------------- render
    /**
     * @param dyn dynamic scale (sprint / attack)
     * @param hit hit marker progress 0..1, or -1 for none
     */
    public static void render(DrawContext ctx, int cx, int cy, ModConfig cfg, float dyn, float hit) {
        float s = cfg.size * dyn;
        CrosshairType type = cfg.crosshairType;
        if (type == CrosshairType.CUSTOM && CrosshairManager.get(cfg.customTexture) == null) type = CrosshairType.CLASSIC;

        int color = cfg.rainbowCrosshair
                ? Ui.alpha(0xFF000000 | Ui.hsv((Util.getMeasuringTimeMs() % 4000L) / 4000f, 0.8f, 1f), cfg.alpha)
                : cfg.argb();
        int outlineColor = Ui.alpha(0xFF000000, cfg.alpha);
        int t = Math.max(1, Math.round(s * cfg.thickness));
        int gap = Math.max(0, Math.round(3f * s * cfg.gap));
        int len = Math.max(1, Math.round(5f * s));

        n = 0;
        switch (type) {
            case DEFAULT -> ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA, cx - 7, cy - 7, 15, 15);
            case DOT -> { dot(cx, cy, s * cfg.thickness); flush(ctx, color, outlineColor, cfg.outline); }
            case CIRCLE -> { ring(cx, cy, s, t, cfg); flush(ctx, color, outlineColor, cfg.outline); }
            case RING_DOT -> { ring(cx, cy, s, t, cfg); dot(cx, cy, s * 0.6f); flush(ctx, color, outlineColor, cfg.outline); }
            case CLASSIC -> { ticks(cx, cy, t, gap, len, true, true, true, true); flush(ctx, color, outlineColor, cfg.outline); }
            case CROSS_DOT -> { ticks(cx, cy, t, gap, len, true, true, true, true); dot(cx, cy, s * 0.6f); flush(ctx, color, outlineColor, cfg.outline); }
            case T_SHAPE -> { ticks(cx, cy, t, gap, len, false, true, true, true); flush(ctx, color, outlineColor, cfg.outline); }
            case X_SHAPE -> { diagonals(cx, cy, Math.max(1, gap), len, t); flush(ctx, color, outlineColor, cfg.outline); }
            case SQUARE -> { square(cx, cy, s, t, cfg); flush(ctx, color, outlineColor, cfg.outline); }
            case CUSTOM -> custom(ctx, cx, cy, s, cfg);
        }
        hitMarker(ctx, cx, cy, s, cfg, hit);
    }

    // ---------------------------------------------------------------- shapes
    private static void dot(int cx, int cy, float s) {
        int h = Math.max(1, Math.round(1.5f * s));
        add(cx - h, cy - h, cx + h, cy + h);
    }

    private static void ticks(int cx, int cy, int t, int gap, int len, boolean top, boolean bottom, boolean left, boolean right) {
        int x0 = cx - t / 2, y0 = cy - t / 2;
        if (top) add(x0, cy - gap - len, x0 + t, cy - gap);
        if (bottom) add(x0, cy + gap, x0 + t, cy + gap + len);
        if (left) add(cx - gap - len, y0, cx - gap, y0 + t);
        if (right) add(cx + gap, y0, cx + gap + len, y0 + t);
    }

    /** Four diagonal arms made of t x t squares. */
    private static void diagonals(int cx, int cy, int d0, int count, int t) {
        for (int k = 0; k < count; k++) {
            int d = d0 + k;
            add(cx + d, cy + d, cx + d + t, cy + d + t);
            add(cx - d - t, cy + d, cx - d, cy + d + t);
            add(cx + d, cy - d - t, cx + d + t, cy - d);
            add(cx - d - t, cy - d - t, cx - d, cy - d);
        }
    }

    private static void ring(int cx, int cy, float s, int t, ModConfig cfg) {
        int r = Math.max(2, Math.round((3f + 2f * cfg.gap) * s));
        int ri = Math.max(0, r - t);
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int y = cy + dy;
            if (Math.abs(dy) < ri) {
                int inner = (int) Math.round(Math.sqrt((double) ri * ri - (double) dy * dy));
                add(cx - outer, y, cx - inner, y + 1);
                add(cx + inner, y, cx + outer, y + 1);
            } else {
                add(cx - outer, y, cx + outer, y + 1);
            }
        }
    }

    private static void square(int cx, int cy, float s, int t, ModConfig cfg) {
        int half = Math.max(2, Math.round((3f + 2f * cfg.gap) * s));
        add(cx - half, cy - half, cx + half, cy - half + t);
        add(cx - half, cy + half - t, cx + half, cy + half);
        add(cx - half, cy - half + t, cx - half + t, cy + half - t);
        add(cx + half - t, cy - half + t, cx + half, cy + half - t);
    }

    // ------------------------------------------------------------ hit marker
    private static void hitMarker(DrawContext ctx, int cx, int cy, float s, ModConfig cfg, float hit) {
        if (!cfg.hitMarker || hit < 0f) return;
        int t = Math.max(1, Math.round(s * cfg.thickness));
        int d0 = Math.round(6f * s) + Math.round(hit * 5f); // arms fly outwards while fading
        n = 0;
        diagonals(cx, cy, d0, 3, t);
        float a = Math.max(0.6f, cfg.alpha) * (1f - hit);
        flush(ctx, Ui.alpha(0xFFFF4545, a), Ui.alpha(0xFF000000, a), cfg.outline);
    }

    // ------------------------------------------------------------ custom PNG
    private static int colorAt(CrosshairManager.CustomCrosshair t, ModConfig cfg, int x, int y) {
        int p = t.argb()[y * t.w() + x];
        int a = p >>> 24;
        if (a == 0) return 0;
        int na = Math.round(a * cfg.alpha);
        if (na == 0) return 0;
        int r = ((p >> 16) & 0xFF) * cfg.red / 255;
        int g = ((p >> 8) & 0xFF) * cfg.green / 255;
        int b = (p & 0xFF) * cfg.blue / 255;
        return (na << 24) | (r << 16) | (g << 8) | b;
    }

    /** PNG drawn as rectangles; horizontal runs of equal color are merged into one fill. */
    private static void custom(DrawContext ctx, int cx, int cy, float s, ModConfig cfg) {
        CrosshairManager.CustomCrosshair t = CrosshairManager.get(cfg.customTexture);
        float cell = 16f * s / Math.max(t.w(), t.h());
        int ox = Math.round(cx - cell * t.w() / 2f);
        int oy = Math.round(cy - cell * t.h() / 2f);
        for (int y = 0; y < t.h(); y++) {
            int y0 = oy + Math.round(y * cell), y1 = oy + Math.round((y + 1) * cell);
            if (y1 <= y0) y1 = y0 + 1;
            int runStart = 0, runCol = colorAt(t, cfg, 0, y);
            for (int x = 1; x <= t.w(); x++) {
                boolean end = x == t.w();
                int col = end ? 0 : colorAt(t, cfg, x, y);
                if (end || col != runCol) {
                    if ((runCol >>> 24) != 0) {
                        int x0 = ox + Math.round(runStart * cell), x1 = ox + Math.round(x * cell);
                        if (x1 <= x0) x1 = x0 + 1;
                        ctx.fill(x0, y0, x1, y1, runCol);
                    }
                    runStart = x;
                    runCol = col;
                }
            }
        }
    }
}
