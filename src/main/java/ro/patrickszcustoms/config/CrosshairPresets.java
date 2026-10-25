package ro.patrickszcustoms.config;

import com.google.gson.Gson;
import net.minecraft.text.Text;
import ro.patrickszcustoms.i18n.Tr;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Built-in crosshair presets and copy/paste share codes ("PC1:" + Base64 JSON). */
public final class CrosshairPresets {
    public enum Preset {
        DEFAULT, MINIMAL, PRECISION, NEON, TACTICAL;

        public Text label() { return Tr.t("patrickszcustoms.preset." + name().toLowerCase()); }
    }

    private static final Gson GSON = new Gson();
    private static final String PREFIX = "PC1:";

    private CrosshairPresets() {}

    /** Plain data for sharing (only crosshair settings, never menu / Spotify / key settings). */
    private static final class Share {
        String type = "CROSS_DOT";
        String texture = "";
        int r = 255, g = 255, b = 255;
        float size = 1f, alpha = 1f, thickness = 1f, gap = 1f, sprint = 1.3f, attack = 0.8f;
        boolean outline = true, hit = true, rainbow = false, dynamic = true;
    }

    public static void apply(Preset p, ModConfig c) {
        copyCrosshair(new ModConfig(), c); // start from defaults
        switch (p) {
            case MINIMAL -> {
                c.crosshairType = CrosshairType.DOT; c.size = 0.9f; c.dynamic = false;
            }
            case PRECISION -> {
                c.crosshairType = CrosshairType.CLASSIC; c.red = 0; c.green = 255; c.blue = 102; c.gap = 1.3f;
            }
            case NEON -> {
                c.crosshairType = CrosshairType.RING_DOT; c.red = 0; c.green = 240; c.blue = 255;
                c.size = 1.2f; c.outline = false;
            }
            case TACTICAL -> {
                c.crosshairType = CrosshairType.T_SHAPE; c.red = 255; c.green = 64; c.blue = 64; c.size = 1.1f;
            }
            default -> { }
        }
        c.clamp();
    }

    private static void copyCrosshair(ModConfig from, ModConfig to) {
        to.crosshairType = from.crosshairType; to.customTexture = from.customTexture;
        to.red = from.red; to.green = from.green; to.blue = from.blue;
        to.size = from.size; to.alpha = from.alpha; to.thickness = from.thickness; to.gap = from.gap;
        to.outline = from.outline; to.hitMarker = from.hitMarker; to.rainbowCrosshair = from.rainbowCrosshair;
        to.dynamic = from.dynamic; to.sprintScale = from.sprintScale; to.attackScale = from.attackScale;
    }

    public static String encode(ModConfig c) {
        Share s = new Share();
        s.type = c.crosshairType.name(); s.texture = c.customTexture;
        s.r = c.red; s.g = c.green; s.b = c.blue;
        s.size = c.size; s.alpha = c.alpha; s.thickness = c.thickness; s.gap = c.gap;
        s.sprint = c.sprintScale; s.attack = c.attackScale;
        s.outline = c.outline; s.hit = c.hitMarker; s.rainbow = c.rainbowCrosshair; s.dynamic = c.dynamic;
        String json = GSON.toJson(s);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** @return true if the code was valid and applied. */
    public static boolean decode(String code, ModConfig c) {
        if (code == null) return false;
        code = code.trim();
        if (!code.startsWith(PREFIX)) return false;
        try {
            String json = new String(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())), StandardCharsets.UTF_8);
            Share s = GSON.fromJson(json, Share.class);
            if (s == null) return false;
            c.crosshairType = CrosshairType.valueOf(s.type); c.customTexture = s.texture == null ? "" : s.texture;
            c.red = s.r; c.green = s.g; c.blue = s.b;
            c.size = s.size; c.alpha = s.alpha; c.thickness = s.thickness; c.gap = s.gap;
            c.sprintScale = s.sprint; c.attackScale = s.attack;
            c.outline = s.outline; c.hitMarker = s.hit; c.rainbowCrosshair = s.rainbow; c.dynamic = s.dynamic;
            c.clamp();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
