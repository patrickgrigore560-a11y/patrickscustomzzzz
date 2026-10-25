package ro.patrickszcustoms.config;

import net.minecraft.util.math.MathHelper;
import ro.patrickszcustoms.animation.AnimationType;
import ro.patrickszcustoms.animation.Easing;

/** Plain data object serialized to config/patrickszcustoms.json with Gson. */
public class ModConfig {
    // --- Crosshair ---
    public CrosshairType crosshairType = CrosshairType.CROSS_DOT;
    /** File name (e.g. "diamond.png") from config/patrickszcustoms/crosshairs/ */
    public String customTexture = "";
    public int red = 255, green = 255, blue = 255;
    public float size = 1.0f;      // 0.5 - 4.0
    public float alpha = 1.0f;     // 0.1 - 1.0
    public boolean dynamic = true;
    public float sprintScale = 1.3f;  // crosshair grows while sprinting
    public float attackScale = 0.8f;  // crosshair shrinks while attacking
    public float thickness = 1.0f;    // line thickness multiplier
    public float gap = 1.0f;          // gap multiplier (classic / cross / X / T, ring radius)
    public boolean outline = true;    // dark outline for visibility
    public boolean hitMarker = true;  // flash when you hit an entity
    public boolean rainbowCrosshair = false;

    // --- Synced lyrics bar (fetched from lrclib.net, off by default) ---
    public boolean lyricsEnabled = false;
    public HudPos lyricsPos = HudPos.TOP_CENTER;
    public float lyricsScale = 1.0f;
    public boolean lyricsChip = true;     // highlight the current word on an accent chip
    public boolean lyricsClock = true;    // real-time clock on the left
    public boolean lyricsElapsed = true;  // song time on the right
    public int lyricsOffsetX = 0, lyricsOffsetY = 0;

    // --- Aim assist (single-player only) ---
    public boolean aimAssistEnabled = false;
    public boolean aimAssistAllEntities = false;
    /** Registry ids of the entity types to assist on (null = not initialised yet -> hostile mobs). */
    public java.util.List<String> aimEntities = null;
    public float aimAssistStrength = 0.35f; // 0.05 - 1
    public float aimAssistRange = 4.5f;     // blocks, 2 - 6
    public float aimAssistFov = 30f;        // cone half-angle in degrees, 5 - 90
    public boolean aimAssistOnlyAttacking = false;
    public boolean aimAssistVertical = true;

    // --- Menu look ---
    public UiTheme theme = UiTheme.NEON;
    public boolean rainbowAccent = false;
    public boolean uiSounds = true; // click sounds in the mod menu
    public Language language = Language.AUTO;

    // --- Spotify HUD ---
    public boolean spotifyHudEnabled = false; // off by default: it spawns a helper process every few seconds
    public HudPos spotifyHudPos = HudPos.TOP_RIGHT;
    public float spotifyHudScale = 1.0f;
    public float spotifyHudAlpha = 0.9f;
    public boolean spotifyHudOnlyPlaying = true;
    public int hudOffsetX = 0, hudOffsetY = 0;

    // --- Menu animations ---
    public boolean animationsEnabled = true;
    public AnimationType animationType = AnimationType.FADE;
    public Easing easing = Easing.CUBIC;
    public int animationDurationMs = 250; // 50 - 1000

    public void clamp() {
        if (crosshairType == null) crosshairType = CrosshairType.CROSS_DOT;
        if (animationType == null) animationType = AnimationType.FADE;
        if (customTexture == null) customTexture = "";
        if (theme == null) theme = UiTheme.NEON;
        if (language == null) language = Language.AUTO;
        if (lyricsPos == null) lyricsPos = HudPos.TOP_CENTER;
        lyricsScale = MathHelper.clamp(lyricsScale, 0.5f, 2.0f);
        lyricsOffsetX = MathHelper.clamp(lyricsOffsetX, -200, 200);
        lyricsOffsetY = MathHelper.clamp(lyricsOffsetY, -200, 200);
        aimAssistStrength = MathHelper.clamp(aimAssistStrength, 0.05f, 1.0f);
        aimAssistRange = MathHelper.clamp(aimAssistRange, 2.0f, 6.0f);
        aimAssistFov = MathHelper.clamp(aimAssistFov, 5.0f, 90.0f);
        if (easing == null) easing = Easing.CUBIC;
        thickness = MathHelper.clamp(thickness, 0.5f, 4.0f);
        gap = MathHelper.clamp(gap, 0.0f, 3.0f);
        hudOffsetX = MathHelper.clamp(hudOffsetX, -200, 200);
        hudOffsetY = MathHelper.clamp(hudOffsetY, -200, 200);
        if (spotifyHudPos == null) spotifyHudPos = HudPos.TOP_RIGHT;
        spotifyHudScale = MathHelper.clamp(spotifyHudScale, 0.5f, 2.5f);
        spotifyHudAlpha = MathHelper.clamp(spotifyHudAlpha, 0.2f, 1.0f);
        red = MathHelper.clamp(red, 0, 255);
        green = MathHelper.clamp(green, 0, 255);
        blue = MathHelper.clamp(blue, 0, 255);
        size = MathHelper.clamp(size, 0.5f, 4.0f);
        alpha = MathHelper.clamp(alpha, 0.1f, 1.0f);
        sprintScale = MathHelper.clamp(sprintScale, 0.5f, 3.0f);
        attackScale = MathHelper.clamp(attackScale, 0.5f, 3.0f);
        animationDurationMs = MathHelper.clamp(animationDurationMs, 50, 1000);
    }

    /** Packed ARGB color for DrawContext#fill. */
    public int argb() {
        int a = Math.round(alpha * 255f);
        return (a << 24) | (red << 16) | (green << 8) | blue;
    }
}
