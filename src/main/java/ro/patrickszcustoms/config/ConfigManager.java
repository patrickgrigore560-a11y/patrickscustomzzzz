package ro.patrickszcustoms.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ro.patrickszcustoms.PatrickszCustomsClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads / saves the mod configuration and owns the crosshair texture folder. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("patrickszcustoms.json");
    /** .minecraft/config/patrickszcustoms/crosshairs/  -> drop your .png files here */
    public static final Path CROSSHAIR_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("patrickszcustoms").resolve("crosshairs");

    private static ModConfig config = new ModConfig();

    private ConfigManager() {}

    public static ModConfig get() { return config; }

    public static void load() {
        try {
            Files.createDirectories(CROSSHAIR_DIR);
            if (Files.exists(CONFIG_FILE)) {
                try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                    ModConfig loaded = GSON.fromJson(r, ModConfig.class);
                    if (loaded != null) config = loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            PatrickszCustomsClient.LOGGER.error("Failed to read config, using defaults", e);
            config = new ModConfig();
        }
        config.clamp();
        save();
    }

    public static void save() {
        config.clamp();
        try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(config, w);
        } catch (IOException e) {
            PatrickszCustomsClient.LOGGER.error("Failed to save config", e);
        }
    }
}
