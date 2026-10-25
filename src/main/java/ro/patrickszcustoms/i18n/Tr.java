package ro.patrickszcustoms.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.Language;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Own translation layer so the mod language can be changed from the mod menu,
 * independent of the game language.
 *
 *  - AUTO: uses vanilla Text.translatable (follows Options > Language).
 *  - Any other language: reads assets/patrickszcustoms/lang/&lt;code&gt;.json directly,
 *    falls back to English for missing keys.
 */
public final class Tr {
    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    private Tr() {}

    public static MutableText t(String key, Object... args) {
        Language lang = ConfigManager.get().language;
        if (lang != Language.AUTO) {
            String s = table(lang.code).get(key);
            if (s == null) s = table(Language.ENGLISH.code).get(key);
            if (s != null) {
                try {
                    return Text.literal(args.length == 0 ? s : String.format(s, args));
                } catch (RuntimeException e) {
                    return Text.literal(s);
                }
            }
        }
        return Text.translatable(key, args);
    }

    private static Map<String, String> table(String code) {
        return TABLES.computeIfAbsent(code, c -> {
            Map<String, String> map = new HashMap<>();
            String path = "/assets/patrickszcustoms/lang/" + c + ".json";
            try (InputStream in = Tr.class.getResourceAsStream(path)) {
                if (in != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : obj.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception ignored) {
                // missing / broken file -> empty table -> English fallback
            }
            return map;
        });
    }
}
