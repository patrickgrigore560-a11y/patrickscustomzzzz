package ro.patrickszcustoms.config;

/** UI languages of the mod. AUTO follows the game language (Options > Language). */
public enum Language {
    AUTO("", "Auto"),
    ENGLISH("en_us", "English"),
    ROMANIAN("ro_ro", "Română"),
    HUNGARIAN("hu_hu", "Magyar"),
    SPANISH("es_es", "Español"),
    JAPANESE("ja_jp", "日本語"),
    CHINESE("zh_cn", "中文 (简体)");

    public final String code;
    public final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }
}
