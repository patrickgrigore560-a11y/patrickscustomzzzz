package ro.patrickszcustoms;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.MinecraftClient;
import ro.patrickszcustoms.animation.ScreenAnimator;
import ro.patrickszcustoms.assist.AimAssist;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.gui.ConfigScreen;
import ro.patrickszcustoms.render.CrosshairManager;
import ro.patrickszcustoms.spotify.SpotifyController;
import ro.patrickszcustoms.spotify.SpotifyHud;

/** Entry point (vibecoded). Config, keybinds (rebindable in-game), HUD overlay, tick hooks. */
public class PatrickszCustomsClient implements ClientModInitializer {
    public static final String MOD_ID = "patrickszcustoms";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openConfigKey, spotifyPrevKey, spotifyPlayKey, spotifyNextKey, aimToggleKey;

    public static KeyBinding getOpenConfigKey() { return openConfigKey; }

    /** Order matches the labels in ConfigScreen: menu, prev, play/pause, next. */
    public static KeyBinding[] getKeys() {
        return new KeyBinding[]{openConfigKey, spotifyPrevKey, spotifyPlayKey, spotifyNextKey, aimToggleKey};
    }

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        CrosshairManager.reload();

        // 1.21.9+ key categories are objects, not strings.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openConfigKey = register("open_config", GLFW.GLFW_KEY_O, category);
        // Spotify keys are unbound by default; bind them in the menu (Keybinds section).
        spotifyPrevKey = register("spotify_prev", GLFW.GLFW_KEY_UNKNOWN, category);
        spotifyPlayKey = register("spotify_play", GLFW.GLFW_KEY_UNKNOWN, category);
        spotifyNextKey = register("spotify_next", GLFW.GLFW_KEY_UNKNOWN, category);
        aimToggleKey = register("aim_toggle", GLFW.GLFW_KEY_UNKNOWN, category);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CrosshairManager.tick(client);
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ConfigScreen(null));
            }
            while (spotifyPrevKey.wasPressed()) SpotifyController.send(SpotifyController.Action.PREVIOUS);
            while (spotifyPlayKey.wasPressed()) SpotifyController.send(SpotifyController.Action.PLAY_PAUSE);
            while (spotifyNextKey.wasPressed()) SpotifyController.send(SpotifyController.Action.NEXT);
            while (aimToggleKey.wasPressed()) AimAssist.toggle(client);
        });

        // One overlay element: closing-animation ghost + Spotify HUD
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "overlay"),
                (ctx, tickCounter) -> {
                    ScreenAnimator.renderGhostOnHud(ctx);
                    SpotifyHud.render(ctx);
                    AimAssist.update(MinecraftClient.getInstance()); // per-frame, single-player only
                });
    }

    private static KeyBinding register(String name, int key, KeyBinding.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, key, category));
    }
}
