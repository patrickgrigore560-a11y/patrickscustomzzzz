package ro.patrickszcustoms.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Util;
import ro.patrickszcustoms.PatrickszCustomsClient;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Cursor manager.
 *
 * Custom PNGs are NOT uploaded as GPU textures (the texture API changed a lot in 1.21.5+).
 * They are read with ImageIO, box-downsampled to at most MAX_GRID x MAX_GRID cells and
 * drawn as filled rectangles. Best results: PNGs up to 32x32.
 */
public final class CrosshairManager {
    public static final int MAX_GRID = 48;

    public record CustomCrosshair(String name, int w, int h, int[] argb) {}

    private static final Map<String, CustomCrosshair> IMAGES = new LinkedHashMap<>();
    private static float targetScale = 1f, scale = 1f;
    private static long lastMs = 0L;
    private static long hitStartMs = -1L;
    private static boolean attackWas;

    private CrosshairManager() {}

    // ---------------------------------------------------------------- loading
    public static void reload() {
        IMAGES.clear();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(ConfigManager.CROSSHAIR_DIR)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted().forEach(files::add);
        } catch (IOException e) {
            PatrickszCustomsClient.LOGGER.warn("Cannot list crosshair folder", e);
            return;
        }
        for (Path p : files) {
            String name = p.getFileName().toString();
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img != null) IMAGES.put(name, downsample(name, img));
            } catch (Exception e) {
                PatrickszCustomsClient.LOGGER.warn("Could not load crosshair '{}'", name, e);
            }
        }
        PatrickszCustomsClient.LOGGER.info("Loaded {} custom crosshair(s)", IMAGES.size());
    }

    private static CustomCrosshair downsample(String name, BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight(), max = Math.max(w, h);
        int gw = w, gh = h;
        if (max > MAX_GRID) {
            double k = (double) MAX_GRID / max;
            gw = Math.max(1, (int) Math.round(w * k));
            gh = Math.max(1, (int) Math.round(h * k));
        }
        int[] out = new int[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int x0 = gx * w / gw, x1 = Math.max(x0 + 1, (gx + 1) * w / gw);
                int y0 = gy * h / gh, y1 = Math.max(y0 + 1, (gy + 1) * h / gh);
                long sa = 0, sr = 0, sg = 0, sb = 0; int n = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int p = img.getRGB(x, y);
                        int a = p >>> 24;
                        sa += a; sr += (long) ((p >> 16) & 0xFF) * a;
                        sg += (long) ((p >> 8) & 0xFF) * a; sb += (long) (p & 0xFF) * a; n++;
                    }
                }
                if (n == 0 || sa == 0) { out[gy * gw + gx] = 0; continue; }
                int a = (int) (sa / n);
                int r = (int) (sr / sa), g = (int) (sg / sa), b = (int) (sb / sa);
                out[gy * gw + gx] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return new CustomCrosshair(name, gw, gh, out);
    }

    public static List<String> names() { return new ArrayList<>(IMAGES.keySet()); }

    public static CustomCrosshair get(String name) { return IMAGES.get(name); }

    // --------------------------------------------------------------- dynamics
    /** Client tick: decide the target scale (sprint grows, attack shrinks). */
    public static void tick(MinecraftClient mc) {
        ModConfig cfg = ConfigManager.get();
        float target = 1f;
        ClientPlayerEntity p = mc.player;
        if (cfg.dynamic && p != null && mc.currentScreen == null) {
            if (p.isSprinting()) target *= cfg.sprintScale;
            if (p.handSwinging || mc.options.attackKey.isPressed()) target *= cfg.attackScale;
        }
        targetScale = target;

        // hit marker: attack pressed while the crosshair is on an entity
        boolean atk = p != null && mc.currentScreen == null && mc.options.attackKey.isPressed();
        if (atk && !attackWas && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            hitStartMs = Util.getMeasuringTimeMs();
        }
        attackWas = atk;
    }

    /** 0..1 while the hit marker animates, -1 when idle. */
    public static float hitProgress() {
        if (hitStartMs < 0L) return -1f;
        float p = (Util.getMeasuringTimeMs() - hitStartMs) / 220f;
        return p >= 1f ? -1f : p;
    }

    /** Call once per rendered frame: frame-rate independent smoothing. */
    public static float currentScale() {
        long now = Util.getMeasuringTimeMs();
        if (lastMs == 0L) lastMs = now;
        float dt = Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        scale += (targetScale - scale) * (1f - (float) Math.exp(-dt * 14f));
        return scale;
    }
}
