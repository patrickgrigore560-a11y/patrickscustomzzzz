package ro.patrickszcustoms.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvents;
import ro.patrickszcustoms.config.ConfigManager;

/**
 * Menu click sounds. Uses vanilla sound events, so they follow the game's volume sliders.
 * Section buttons play a soft chime on a pentatonic scale (C D E G A) so switching feels musical.
 */
public final class UiSounds {
    private static final float[] PENTATONIC = {1.000f, 1.122f, 1.260f, 1.498f, 1.682f, 2.000f};
    private static long lastTick;

    private UiSounds() {}

    private static boolean enabled() { return ConfigManager.get().uiSounds; }

    /** Normal button click (pitch 1.0 = vanilla). */
    public static void click(SoundManager sm, float pitch) {
        if (!enabled()) return;
        sm.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
    }

    /** Sidebar section chime; index picks the note. */
    public static void chime(SoundManager sm, int index) {
        if (!enabled()) return;
        float pitch = PENTATONIC[Math.floorMod(index, PENTATONIC.length)];
        sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), pitch));
    }

    /** Slider tick: higher value = higher pitch, throttled so dragging is not noisy. */
    public static void tick(double value01) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastTick < 80L) return;
        lastTick = now;
        float pitch = (float) (0.7 + 0.9 * Math.max(0.0, Math.min(1.0, value01)));
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
    }
}
