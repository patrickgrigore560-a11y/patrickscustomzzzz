package ro.patrickszcustoms.gui;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ro.patrickszcustoms.PatrickszCustomsClient;
import ro.patrickszcustoms.animation.AnimationType;
import ro.patrickszcustoms.animation.Easing;
import ro.patrickszcustoms.config.CrosshairPresets;
import ro.patrickszcustoms.config.AimEntities;
import ro.patrickszcustoms.animation.ScreenAnimator;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.CrosshairType;
import ro.patrickszcustoms.config.HudPos;
import ro.patrickszcustoms.config.Language;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.config.UiTheme;
import ro.patrickszcustoms.render.CrosshairManager;
import ro.patrickszcustoms.render.CrosshairRenderer;
import ro.patrickszcustoms.spotify.LyricsHud;
import ro.patrickszcustoms.spotify.LyricsService;
import ro.patrickszcustoms.spotify.SpotifyController;
import ro.patrickszcustoms.spotify.SpotifyHud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Vibecoded settings hub: animated background, sidebar with sections
 * (Cursor Edit / Menu Animations / Spotify HUD / Theme / Keybinds) and live previews.
 */
public class ConfigScreen extends Screen {
    private enum Section {
        CURSOR("patrickszcustoms.section.cursor"),
        STYLE("patrickszcustoms.section.style"),
        PRESETS("patrickszcustoms.section.presets"),
        AIM("patrickszcustoms.section.aim"),
        ANIMATION("patrickszcustoms.section.animation"),
        SPOTIFY("patrickszcustoms.section.spotify"),
        LYRICS("patrickszcustoms.section.lyrics"),
        THEME("patrickszcustoms.section.theme"),
        CONTROLS("patrickszcustoms.section.controls"),
        LANGUAGE("patrickszcustoms.section.language");

        final String key;
        Section(String key) { this.key = key; }
    }

    private static Section current = Section.CURSOR;

    private final Screen parent;
    private KeyBinding waitingBind;
    private boolean introPlayed;
    private long contentStart;
    private int contentDir = 1;
    private Text statusText;
    private long statusMs;

    private int cx0, cy0, cw, ch;          // card
    private int contentX, contentW, ctrlW; // content area
    private int px1, py1, px2, py2;        // preview box

    public ConfigScreen(Screen parent) {
        super(Tr.t("patrickszcustoms.config.title"));
        this.parent = parent;
        Ui.screenOpenMs = Util.getMeasuringTimeMs();
        contentStart = Ui.screenOpenMs;
    }

    // ================================================================= layout
    @Override
    protected void init() {
        cw = Math.min(width - 16, 440);
        ch = Math.min(height - 16, 262);
        cx0 = (width - cw) / 2;
        cy0 = (height - ch) / 2;

        int sbX = cx0 + 8, sbW = 96;
        contentX = sbX + sbW + 12;
        contentW = cx0 + cw - 8 - contentX;
        ctrlW = Math.min(172, contentW);

        Section[] all = Section.values();
        int step = Math.min(26, Math.max(20, (ch - 34 - 38) / all.length));
        int sbH = Math.min(22, step - 2);
        for (int i = 0; i < all.length; i++) {
            Section s = all[i];
            addDrawableChild(new FancyButton(sbX, cy0 + 34 + i * step, sbW, sbH,
                    Tr.t(s.key), b -> switchTo(s))
                    .mode(FancyButton.Mode.SIDEBAR)
                    .intro(introPlayed ? -1 : i * 60)
                    .sound(i)
                    .selected(s == current));
        }
        addDrawableChild(new FancyButton(cx0 + cw - 8 - 110, cy0 + ch - 28, 110, 20, Tr.t("patrickszcustoms.done"),
                b -> close()).pitch(0.8f));

        ModConfig c = ConfigManager.get();
        int x = contentX, y = cy0 + 34;
        switch (current) {
            case CURSOR -> cursorSection(c, x, y);
            case STYLE -> styleSection(c, x, y);
            case PRESETS -> presetsSection(c, x, y);
            case AIM -> aimSection(c, x, y);
            case ANIMATION -> animationSection(c, x, y);
            case SPOTIFY -> spotifySection(c, x, y);
            case LYRICS -> lyricsSection(c, x, y);
            case THEME -> themeSection(c, x, y);
            case CONTROLS -> controlsSection(x, y);
            case LANGUAGE -> languageSection(c, x, y);
        }

        px1 = contentX + ctrlW + 10;
        px2 = cx0 + cw - 8;
        py1 = cy0 + 34;
        py2 = cy0 + ch - 36;
        introPlayed = true;
    }

    private void switchTo(Section s) {
        if (s == current) return;
        contentDir = s.ordinal() > current.ordinal() ? 1 : -1;
        current = s;
        contentStart = Util.getMeasuringTimeMs();
        waitingBind = null;
        clearAndInit();
    }

    // ----------------------------------------------------------- widget helpers
    private FancyButton button(int x, int y, int w, Text label, Runnable action) {
        return new FancyButton(x, y, w, 20, label, b -> action.run()).mode(FancyButton.Mode.CONTENT);
    }

    private <T> FancyButton cycle(int x, int y, int w, Text label, T[] values,
                                  Supplier<T> get, Consumer<T> set, Function<T, Text> name) {
        FancyButton btn = new FancyButton(x, y, w, 20, Text.empty(), b -> {
            int idx = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(get.get())) idx = i;
            T next = values[(idx + 1) % values.length];
            set.accept(next);
            b.setMessage(Text.literal("").append(label).append(Text.literal(": ")).append(name.apply(next)));
        }).mode(FancyButton.Mode.CONTENT);
        btn.setMessage(Text.literal("").append(label).append(Text.literal(": ")).append(name.apply(get.get())));
        return btn;
    }

    private FancyButton toggle(int x, int y, int w, Text label, Supplier<Boolean> get, Consumer<Boolean> set) {
        return cycle(x, y, w, label, new Boolean[]{false, true}, get, set,
                v -> Tr.t(v ? "patrickszcustoms.on" : "patrickszcustoms.off"));
    }

    private FancySlider slider(int x, int y, int w, double min, double max, double cur,
                               Function<Double, Text> label, Consumer<Double> onChange) {
        return new FancySlider(x, y, w, 20, min, max, cur, label, onChange);
    }

    // ============================================================== sections
    private void cursorSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.type"),
                CrosshairType.values(), () -> c.crosshairType, v -> c.crosshairType = v, CrosshairType::label));
        y += 22;

        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(CrosshairManager.names());
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.texture"),
                names.toArray(new String[0]),
                () -> names.contains(c.customTexture) ? c.customTexture : "",
                v -> {
                    c.customTexture = v;
                    if (!v.isEmpty()) c.crosshairType = CrosshairType.CUSTOM;
                    clearAndInit();
                },
                n -> n.isEmpty() ? Tr.t("patrickszcustoms.none") : Text.literal(n)));
        y += 22;

        int sw = (w - 4) / 3;
        addDrawableChild(slider(x, y, sw, 0, 255, c.red,
                v -> Text.literal("R " + Math.round(v)), v -> c.red = (int) Math.round(v)));
        addDrawableChild(slider(x + sw + 2, y, sw, 0, 255, c.green,
                v -> Text.literal("G " + Math.round(v)), v -> c.green = (int) Math.round(v)));
        addDrawableChild(slider(x + 2 * (sw + 2), y, sw, 0, 255, c.blue,
                v -> Text.literal("B " + Math.round(v)), v -> c.blue = (int) Math.round(v)));
        y += 22;

        addDrawableChild(slider(x, y, w, 0.5, 4.0, c.size,
                v -> Tr.t("patrickszcustoms.option.size", String.format("%.2fx", v)),
                v -> c.size = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.1, 1.0, c.alpha,
                v -> Tr.t("patrickszcustoms.option.alpha", Math.round(v * 100) + "%"),
                v -> c.alpha = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.dynamic"),
                () -> c.dynamic, v -> c.dynamic = v));
        y += 22;
        addDrawableChild(button(x, y, w / 2 - 1, Tr.t("patrickszcustoms.button.reload"),
                () -> { CrosshairManager.reload(); clearAndInit(); }));
        addDrawableChild(button(x + w / 2 + 1, y, w / 2 - 1, Tr.t("patrickszcustoms.button.folder"),
                () -> client.keyboard.setClipboard(ConfigManager.CROSSHAIR_DIR.toAbsolutePath().toString())));
    }

    private void animationSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.anim_enabled"),
                () -> c.animationsEnabled, v -> c.animationsEnabled = v));
        y += 22;
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.anim_type"),
                AnimationType.values(), () -> c.animationType, v -> c.animationType = v, AnimationType::label));
        y += 22;
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.easing"),
                Easing.values(), () -> c.easing, v -> c.easing = v, Easing::label));
        y += 22;
        addDrawableChild(slider(x, y, w, 50, 1000, c.animationDurationMs,
                v -> Tr.t("patrickszcustoms.option.duration", (int) (Math.round(v / 10) * 10)),
                v -> c.animationDurationMs = (int) (Math.round(v / 10) * 10)));
        y += 22;
        addDrawableChild(button(x, y, w, Tr.t("patrickszcustoms.button.test"),
                () -> client.setScreen(new AnimationTestScreen(this))));
    }

    private void spotifySection(ModConfig c, int x, int y) {
        int w = ctrlW;
        int mid = Math.max(60, w / 3 + 10), side = (w - mid - 4) / 2;
        addDrawableChild(button(x, y, side, Tr.t("patrickszcustoms.spotify.prev"),
                () -> SpotifyController.send(SpotifyController.Action.PREVIOUS)));
        addDrawableChild(button(x + side + 2, y, mid, Tr.t("patrickszcustoms.spotify.playpause"),
                () -> SpotifyController.send(SpotifyController.Action.PLAY_PAUSE)));
        addDrawableChild(button(x + side + mid + 4, y, side, Tr.t("patrickszcustoms.spotify.next"),
                () -> SpotifyController.send(SpotifyController.Action.NEXT)));
        y += 26;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.hud_enabled"),
                () -> c.spotifyHudEnabled, v -> c.spotifyHudEnabled = v));
        y += 22;
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.hud_pos"),
                HudPos.values(), () -> c.spotifyHudPos, v -> c.spotifyHudPos = v, HudPos::label));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.5, 2.5, c.spotifyHudScale,
                v -> Tr.t("patrickszcustoms.option.hud_scale", String.format("%.2fx", v)),
                v -> c.spotifyHudScale = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.2, 1.0, c.spotifyHudAlpha,
                v -> Tr.t("patrickszcustoms.option.alpha", Math.round(v * 100) + "%"),
                v -> c.spotifyHudAlpha = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.hud_only_playing"),
                () -> c.spotifyHudOnlyPlaying, v -> c.spotifyHudOnlyPlaying = v));
        y += 22;
        addDrawableChild(slider(x, y, w, -150, 150, c.hudOffsetX,
                v -> Tr.t("patrickszcustoms.option.hud_offset_x", String.format("%+d px", Math.round(v))),
                v -> c.hudOffsetX = (int) Math.round(v)));
        y += 22;
        addDrawableChild(slider(x, y, w, -150, 150, c.hudOffsetY,
                v -> Tr.t("patrickszcustoms.option.hud_offset_y", String.format("%+d px", Math.round(v))),
                v -> c.hudOffsetY = (int) Math.round(v)));
    }

    private void themeSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        for (UiTheme t : UiTheme.values()) {
            FancyButton b = new FancyButton(x, y, w, 20, t.label(), btn -> { c.theme = t; clearAndInit(); })
                    .mode(FancyButton.Mode.CONTENT).selected(c.theme == t);
            addDrawableChild(b);
            y += 22;
        }
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.rainbow"),
                () -> c.rainbowAccent, v -> c.rainbowAccent = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.ui_sounds"),
                () -> c.uiSounds, v -> { c.uiSounds = v; UiSounds.click(client.getSoundManager(), 1.2f); }));
    }

    private void controlsSection(int x, int y) {
        int w = ctrlW;
        KeyBinding[] keys = PatrickszCustomsClient.getKeys();
        String[] labels = {"menu", "spotify_prev", "spotify_play", "spotify_next", "aim_toggle"};
        for (int i = 0; i < keys.length; i++) {
            KeyBinding k = keys[i];
            Text name = Tr.t("patrickszcustoms.key." + labels[i]);
            Text value = waitingBind == k
                    ? Text.literal("> ").append(Tr.t("patrickszcustoms.controls.press")).append(Text.literal(" <"))
                    : k.getBoundKeyLocalizedText();
            addDrawableChild(button(x, y, w, Text.literal("").append(name).append(Text.literal(": ")).append(value),
                    () -> { waitingBind = k; clearAndInit(); }));
            y += 22;
        }
        addDrawableChild(button(x, y, w, Tr.t("patrickszcustoms.button.reset_keys"), () -> {
            waitingBind = null;
            for (KeyBinding k : keys) k.setBoundKey(k.getDefaultKey());
            applyKeyChange();
            clearAndInit();
        }));
    }

    private void languageSection(ModConfig c, int x, int y) {
        for (Language l : Language.values()) {
            Text label = l == Language.AUTO ? Tr.t("patrickszcustoms.language.auto") : Text.literal(l.nativeName);
            addDrawableChild(new FancyButton(x, y, ctrlW, 20, label, b -> { c.language = l; clearAndInit(); })
                    .mode(FancyButton.Mode.CONTENT).selected(c.language == l));
            y += 22;
        }
    }

    /** Fine-tuning of the crosshair shape. */
    private void styleSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        addDrawableChild(slider(x, y, w, 0.5, 4.0, c.thickness,
                v -> Tr.t("patrickszcustoms.option.thickness", String.format("%.2fx", v)),
                v -> c.thickness = (float) (Math.round(v * 4) / 4.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.0, 3.0, c.gap,
                v -> Tr.t("patrickszcustoms.option.gap", String.format("%.1fx", v)),
                v -> c.gap = (float) (Math.round(v * 10) / 10.0)));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.outline"),
                () -> c.outline, v -> c.outline = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.hit_marker"),
                () -> c.hitMarker, v -> c.hitMarker = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.rainbow_crosshair"),
                () -> c.rainbowCrosshair, v -> c.rainbowCrosshair = v));
        y += 22;
        addDrawableChild(slider(x, y, w, 1.0, 2.0, c.sprintScale,
                v -> Tr.t("patrickszcustoms.option.sprint_scale", String.format("%.2fx", v)),
                v -> c.sprintScale = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.5, 1.0, c.attackScale,
                v -> Tr.t("patrickszcustoms.option.attack_scale", String.format("%.2fx", v)),
                v -> c.attackScale = (float) (Math.round(v * 20) / 20.0)));
    }

    /** One-click presets + copy/paste share codes. */
    private void presetsSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        for (CrosshairPresets.Preset p : CrosshairPresets.Preset.values()) {
            addDrawableChild(button(x, y, w, p.label(), () -> {
                CrosshairPresets.apply(p, c);
                setStatus(Tr.t("patrickszcustoms.status.preset", p.label().getString()));
            }));
            y += 22;
        }
        y += 4;
        addDrawableChild(button(x, y, w, Tr.t("patrickszcustoms.button.copy_code"), () -> {
            client.keyboard.setClipboard(CrosshairPresets.encode(c));
            setStatus(Tr.t("patrickszcustoms.status.copied"));
        }));
        y += 22;
        addDrawableChild(button(x, y, w, Tr.t("patrickszcustoms.button.paste_code"), () -> {
            boolean ok = CrosshairPresets.decode(client.keyboard.getClipboard(), c);
            setStatus(Tr.t(ok ? "patrickszcustoms.status.pasted" : "patrickszcustoms.status.invalid"));
        }));
    }

    /** Aim assist settings (single-player only). */
    private void aimSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.aim_enabled"),
                () -> c.aimAssistEnabled, v -> c.aimAssistEnabled = v));
        y += 22;
        AimEntities.ensureDefaults(c);
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.aim_all_entities"),
                () -> c.aimAssistAllEntities, v -> { c.aimAssistAllEntities = v; clearAndInit(); }));
        y += 22;
        FancyButton choose = button(x, y, w, Tr.t("patrickszcustoms.button.aim_choose", c.aimEntities.size()),
                () -> client.setScreen(new EntityPickerScreen(this)));
        choose.active = !c.aimAssistAllEntities;
        addDrawableChild(choose);
        y += 22;
        addDrawableChild(slider(x, y, w, 0.05, 1.0, c.aimAssistStrength,
                v -> Tr.t("patrickszcustoms.option.aim_strength", Math.round(v * 100) + "%"),
                v -> c.aimAssistStrength = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 2.0, 6.0, c.aimAssistRange,
                v -> Tr.t("patrickszcustoms.option.aim_range", String.format("%.1f", v)),
                v -> c.aimAssistRange = (float) (Math.round(v * 2) / 2.0)));
        y += 22;
        addDrawableChild(slider(x, y, w, 5, 90, c.aimAssistFov,
                v -> Tr.t("patrickszcustoms.option.aim_fov", Math.round(v / 5) * 5),
                v -> c.aimAssistFov = (float) (Math.round(v / 5) * 5)));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.aim_only_attacking"),
                () -> c.aimAssistOnlyAttacking, v -> c.aimAssistOnlyAttacking = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.aim_vertical"),
                () -> c.aimAssistVertical, v -> c.aimAssistVertical = v));
    }

    /** Top-down diagram of the assist cone with a moving mob. */
    private void previewAim(DrawContext ctx, long now) {
        box(ctx);
        ModConfig c = ConfigManager.get();
        int acc = Ui.accent() | 0xFF000000;
        int pw = px2 - px1;
        drawWrapped(ctx, Tr.t("patrickszcustoms.aim.hint"), px1 + 6, py1 + 6, pw - 12, 0xFFB8BCD0);

        boolean ok = client.isInSingleplayer();
        ctx.drawCenteredTextWithShadow(textRenderer,
                Tr.t(ok ? "patrickszcustoms.aim.status_ready" : "patrickszcustoms.aim.status_blocked"),
                (px1 + px2) / 2, py2 - 14, ok ? 0xFF55FF55 : 0xFFFF6B6B);

        int cx = (px1 + px2) / 2, baseY = py2 - 26;
        int len = Math.min(70, baseY - (py1 + 58));
        if (len < 12) return;
        double fov = Math.min(c.aimAssistFov, 85.0);
        double tan = Math.tan(Math.toRadians(fov));
        int maxHw = pw / 2 - 6;
        for (int r = 0; r <= len; r++) {
            int hw = Math.min(maxHw, (int) Math.round(r * tan));
            int row = baseY - r;
            ctx.fill(cx - hw, row, cx + hw + 1, row + 1, Ui.alpha(acc, c.aimAssistEnabled ? 0.28f : 0.10f));
            ctx.fill(cx - hw, row, cx - hw + 1, row + 1, Ui.alpha(acc, 0.9f));
            ctx.fill(cx + hw, row, cx + hw + 1, row + 1, Ui.alpha(acc, 0.9f));
        }
        ctx.fill(cx - 3, baseY, cx + 4, baseY + 5, 0xFFFFFFFF); // player

        double ang = Math.sin(now / 900.0) * Math.min(c.aimAssistFov * 1.4, 80.0); // mob wanders in and out
        int dist = (int) (len * 0.85);
        int mx = cx + (int) Math.round(Math.tan(Math.toRadians(ang)) * dist);
        int my = baseY - dist;
        boolean inside = Math.abs(ang) <= c.aimAssistFov;
        int mobColor = inside ? 0xFF55FF55 : 0xFFFF6B6B;
        ctx.fill(mx - 3, my - 3, mx + 4, my + 4, mobColor);
    }

    /** Synced lyrics bar settings. */
    private void lyricsSection(ModConfig c, int x, int y) {
        int w = ctrlW;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.lyrics_enabled"),
                () -> c.lyricsEnabled, v -> c.lyricsEnabled = v));
        y += 22;
        addDrawableChild(cycle(x, y, w, Tr.t("patrickszcustoms.option.hud_pos"),
                HudPos.values(), () -> c.lyricsPos, v -> c.lyricsPos = v, HudPos::label));
        y += 22;
        addDrawableChild(slider(x, y, w, 0.5, 2.0, c.lyricsScale,
                v -> Tr.t("patrickszcustoms.option.lyrics_scale", String.format("%.2fx", v)),
                v -> c.lyricsScale = (float) (Math.round(v * 20) / 20.0)));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.lyrics_chip"),
                () -> c.lyricsChip, v -> c.lyricsChip = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.lyrics_clock"),
                () -> c.lyricsClock, v -> c.lyricsClock = v));
        y += 22;
        addDrawableChild(toggle(x, y, w, Tr.t("patrickszcustoms.option.lyrics_elapsed"),
                () -> c.lyricsElapsed, v -> c.lyricsElapsed = v));
        y += 22;
        addDrawableChild(slider(x, y, w, -150, 150, c.lyricsOffsetX,
                v -> Tr.t("patrickszcustoms.option.hud_offset_x", String.format("%+d px", Math.round(v))),
                v -> c.lyricsOffsetX = (int) Math.round(v)));
        y += 22;
        addDrawableChild(slider(x, y, w, -150, 150, c.lyricsOffsetY,
                v -> Tr.t("patrickszcustoms.option.hud_offset_y", String.format("%+d px", Math.round(v))),
                v -> c.lyricsOffsetY = (int) Math.round(v)));
    }

    /** Sample lyric bar animating in the preview + live lookup status for the current song. */
    private void previewLyrics(DrawContext ctx, long now) {
        box(ctx);
        ModConfig c = ConfigManager.get();
        int acc = Ui.accent() | 0xFF000000;
        int pw = px2 - px1;
        drawWrapped(ctx, Tr.t("patrickszcustoms.lyrics.privacy"), px1 + 6, py1 + 6, pw - 12, 0xFFB8BCD0);

        SpotifyController.Status s = SpotifyController.getStatus();
        boolean active = s.state() == SpotifyController.State.PLAYING || s.state() == SpotifyController.State.PAUSED;
        Text status;
        if (!c.lyricsEnabled) {
            status = Tr.t("patrickszcustoms.lyrics.state_off");
        } else if (!active) {
            SpotifyController.pollIfDue(1000L);
            status = Tr.t("patrickszcustoms.lyrics.state_idle");
        } else {
            SpotifyController.pollIfDue(1000L);
            String title = s.title().isEmpty() ? s.track() : s.title();
            LyricsService.Result r = LyricsService.get(s.artist(), title, s.durationMs());
            status = switch (r.state()) {
                case LOADING -> Tr.t("patrickszcustoms.lyrics.state_loading");
                case FOUND -> Tr.t("patrickszcustoms.lyrics.state_found");
                case NONE -> Tr.t("patrickszcustoms.lyrics.state_none");
            };
        }
        ctx.drawCenteredTextWithShadow(textRenderer, status, (px1 + px2) / 2, py2 - 14, acc);

        int by1 = py1 + 64, by2 = py2 - 24;
        if (by2 - by1 < 30) return;
        ctx.enableScissor(px1 + 1, by1, px2 - 1, by2);
        ctx.fillGradient(px1 + 1, by1, px2 - 1, by2, 0xFF3A4A66, 0xFF1E2A22);
        String sample = Tr.t("patrickszcustoms.lyrics.sample").getString();
        long cycle = 3200L, ph = now % cycle;
        float prog = Math.min(1f, ph / 2400f);
        LyricsHud.drawBar(ctx, px1 + 1, by1, pw - 2, by2 - by1, sample, sample, prog, now - ph, true, (now / 1000L % 200L) * 1000L);
        ctx.disableScissor();
    }

    private void setStatus(Text t) {
        statusText = t;
        statusMs = Util.getMeasuringTimeMs();
    }

    private void applyKeyChange() {
        KeyBinding.updateKeysByCode();
        client.options.write(); // persisted in options.txt like any vanilla keybind
    }

    /** While waiting, the next key press becomes the binding (ESC cancels). */
    @Override
    public boolean keyPressed(KeyInput input) {
        if (waitingBind != null) {
            int key = input.key();
            KeyBinding target = waitingBind;
            waitingBind = null;
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                target.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(key));
                applyKeyChange();
            }
            clearAndInit();
            return true;
        }
        return super.keyPressed(input);
    }

    // ================================================================= render
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        Ui.contentT = ScreenAnimator.easeOut(MathHelper.clamp((now - contentStart) / 280f, 0f, 1f));
        Ui.contentDir = contentDir;

        Ui.drawBackground(ctx, width, height); // replaces vanilla blur/dim
        drawCard(ctx, now);
        drawHeader(ctx);

        // preview, with the same slide/fade as the controls
        float outer = ScreenAnimator.getAlpha();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((1f - Ui.contentT) * 28f * contentDir, 0f);
        ScreenAnimator.setAlpha(outer * Ui.contentT);
        drawPreview(ctx, now);
        ctx.getMatrices().popMatrix();
        ScreenAnimator.setAlpha(outer);

        // widgets (no super.render: we own the background)
        for (Element e : children()) {
            if (e instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }
    }

    private void drawCard(DrawContext ctx, long now) {
        int acc = Ui.accent() | 0xFF000000;
        Ui.rect(ctx, cx0, cy0, cw, ch, 0xD40A0A12);
        float pulse = 0.55f + 0.30f * (float) Math.sin(now / 600.0);
        ctx.drawStrokedRectangle(cx0, cy0, cw, ch, Ui.alpha(acc, pulse));
        // top accent line with a moving highlight
        ctx.fill(cx0 + 1, cy0 + 1, cx0 + cw - 1, cy0 + 3, Ui.alpha(acc, 0.85f));
        int hx = cx0 + 1 + (int) ((now / 12L) % (cw + 60)) - 60;
        int hx0 = Math.max(cx0 + 1, hx), hx1 = Math.min(cx0 + cw - 1, hx + 60);
        if (hx1 > hx0) ctx.fill(hx0, cy0 + 1, hx1, cy0 + 3, 0xCCFFFFFF);
        // sidebar separator
        ctx.fill(cx0 + 8 + 96 + 5, cy0 + 34, cx0 + 8 + 96 + 6, cy0 + ch - 34, Ui.alpha(acc, 0.25f));
    }

    private void drawHeader(DrawContext ctx) {
        int acc = Ui.accent() | 0xFF000000;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx0 + 10, cy0 + 8);
        ctx.getMatrices().scale(1.25f, 1.25f);
        ctx.drawTextWithShadow(textRenderer, Tr.t("patrickszcustoms.config.title"), 0, 0, acc);
        ctx.getMatrices().popMatrix();
        ctx.drawTextWithShadow(textRenderer, Tr.t(current.key), cx0 + 12, cy0 + 22, 0xFF9AA0B8);
    }

    private void box(DrawContext ctx) {
        int acc = Ui.accent() | 0xFF000000;
        ctx.fill(px1, py1, px2, py2, 0x88000000);
        ctx.drawStrokedRectangle(px1, py1, px2 - px1, py2 - py1, Ui.alpha(acc, 0.6f));
    }

    private void drawPreview(DrawContext ctx, long now) {
        int pw = px2 - px1;
        if (pw < 30) return;
        switch (current) {
            case CURSOR, STYLE, PRESETS -> previewCrosshair(ctx, now);
            case AIM -> previewAim(ctx, now);
            case ANIMATION -> {
                box(ctx);
                ctx.enableScissor(px1 + 1, py1 + 1, px2 - 1, py2 - 1);
                previewAnimation(ctx, now);
                ctx.disableScissor();
            }
            case SPOTIFY -> previewSpotify(ctx, now);
            case LYRICS -> previewLyrics(ctx, now);
            case THEME -> previewTheme(ctx, now);
            case CONTROLS -> drawWrapped(ctx, Tr.t("patrickszcustoms.controls.hint"),
                    px1, py1, pw, 0xFFCCCCCC);
            case LANGUAGE -> {
                box(ctx);
                int acc = Ui.accent() | 0xFF000000;
                int cx = (px1 + px2) / 2, cy = (py1 + py2) / 2;
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(cx, cy - 22);
                ctx.getMatrices().scale(2f, 2f);
                ctx.drawCenteredTextWithShadow(textRenderer, Tr.t("patrickszcustoms.language.hello"), 0, 0, acc);
                ctx.getMatrices().popMatrix();
                drawWrapped(ctx, Tr.t("patrickszcustoms.language.note"), px1 + 8, cy + 6, pw - 16, 0xFFCCCCCC);
            }
        }
    }

    private void previewCrosshair(DrawContext ctx, long now) {
        box(ctx);
        ctx.enableScissor(px1 + 1, py1 + 1, px2 - 1, py2 - 1);
        ModConfig c = ConfigManager.get();
        ctx.fillGradient(px1 + 1, py1 + 1, px2 - 1, py2 - 1, 0xFF5B8DB8, 0xFF2E5A34);
        float dyn = c.dynamic ? 1f + 0.25f * (float) Math.sin(now / 350.0) : 1f;
        long ph = now % 1800L;
        float hit = ph < 220L ? ph / 220f : -1f; // simulated hit marker every 1.8 s
        CrosshairRenderer.render(ctx, (px1 + px2) / 2, (py1 + py2) / 2, c, dyn, hit);
        ctx.disableScissor();
        if (statusText != null && now - statusMs < 3500L) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusText, (px1 + px2) / 2, py2 - 14, Ui.accent() | 0xFF000000);
        }
    }

    /** Loops open -> hold -> close with the exact transform used by real menus. */
    private void previewAnimation(DrawContext ctx, long now) {
        ModConfig c = ConfigManager.get();
        float ph = (now % 2400L) / 2400f;
        float v;
        if (ph < 0.3f) v = ScreenAnimator.easeOpen(ph / 0.3f);
        else if (ph < 0.7f) v = 1f;
        else v = 1f - ScreenAnimator.easeClose((ph - 0.7f) / 0.3f);
        if (!c.animationsEnabled) v = 1f;

        int pw = px2 - px1, phh = py2 - py1;
        float outer = ScreenAnimator.getAlpha();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(px1, py1);
        ScreenAnimator.applyTransform(ctx, v, c.animationType, pw, phh);
        ScreenAnimator.setAlpha(v * outer);
        int x1 = pw / 2 - 60, y1 = phh / 2 - 35;
        ctx.fill(x1, y1, x1 + 120, y1 + 70, 0xFF2B2B2B);
        ctx.drawStrokedRectangle(x1, y1, 120, 70, Ui.accent() | 0xFF000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Tr.t("patrickszcustoms.preview.inventory"), pw / 2, phh / 2 - 4, 0xFFFFFFFF);
        ctx.getMatrices().popMatrix();
        ScreenAnimator.setAlpha(outer);
    }

    private void previewSpotify(DrawContext ctx, long now) {
        int pw = px2 - px1;
        int acc = Ui.accent() | 0xFF000000;
        ctx.drawTextWithShadow(textRenderer, Tr.t("patrickszcustoms.spotify.header"), px1, py1, acc);
        SpotifyController.pollIfDue();
        drawWrapped(ctx, spotifyStatusText(), px1, py1 + 12, pw, 0xFFFFFFFF);

        int by1 = py1 + 40;
        ctx.fill(px1, by1, px2, py2, 0x88000000);
        ctx.drawStrokedRectangle(px1, by1, pw, py2 - by1, Ui.alpha(acc, 0.6f));
        ctx.enableScissor(px1 + 1, by1 + 1, px2 - 1, py2 - 1);
        ctx.fillGradient(px1 + 1, by1 + 1, px2 - 1, py2 - 1, 0xFF3A4A66, 0xFF1E2A22);
        SpotifyController.Status st = SpotifyController.getStatus();
        boolean real = st.state() == SpotifyController.State.PLAYING;
        String text = real ? st.track() : Tr.t("patrickszcustoms.spotify.sample").getString();
        SpotifyHud.drawPill(ctx, px1, by1, pw, py2 - by1, text, true, now - (now % 4000L));
        ctx.disableScissor();
    }

    private void previewTheme(DrawContext ctx, long now) {
        box(ctx);
        ModConfig c = ConfigManager.get();
        float k = 0.5f + 0.5f * (float) Math.sin(now / 1000.0 * 0.35);
        ctx.fillGradient(px1 + 1, py1 + 1, px2 - 1, py2 - 1,
                Ui.mix(c.theme.top, c.theme.bottom, k), Ui.mix(c.theme.bottom, c.theme.top, k));
        int acc = Ui.accent() | 0xFF000000;
        int cx = (px1 + px2) / 2, cy = (py1 + py2) / 2;
        Ui.rect(ctx, cx - 50, cy - 24, 100, 48, 0xD40A0A12);
        ctx.fill(cx - 50, cy - 24, cx + 50, cy - 22, acc);
        ctx.drawCenteredTextWithShadow(textRenderer, c.theme.label(), cx, cy - 8, acc);
        ctx.fill(cx - 36, cy + 6, cx + 36, cy + 8, Ui.alpha(acc, 0.7f));
        ctx.fill(cx - 36, cy + 12, cx + 12, cy + 14, Ui.alpha(acc, 0.35f));
    }

    // -------------------------------------------------------------- text utils
    private Text spotifyStatusText() {
        SpotifyController.Status st = SpotifyController.getStatus();
        return switch (st.state()) {
            case UNKNOWN -> Tr.t("patrickszcustoms.spotify.checking");
            case NOT_RUNNING -> Tr.t("patrickszcustoms.spotify.not_running");
            case RUNNING_NO_INFO -> Tr.t("patrickszcustoms.spotify.no_info");
            case PAUSED -> Tr.t("patrickszcustoms.spotify.paused");
            case PLAYING -> Tr.t("patrickszcustoms.spotify.playing", st.track());
        };
    }

    private void drawWrapped(DrawContext ctx, Text text, int x, int y, int width, int color) {
        for (OrderedText line : textRenderer.wrapLines(text, Math.max(40, width))) {
            ctx.drawTextWithShadow(textRenderer, line, x, y, color);
            y += textRenderer.fontHeight + 2;
        }
    }

    // ------------------------------------------------------------------ close
    @Override
    public void removed() { ConfigManager.save(); }

    @Override
    public void close() { client.setScreen(parent); }
}
