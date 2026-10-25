package ro.patrickszcustoms.config;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.text.Text;

/** Menu color themes: gradient top, gradient bottom, accent. */
public enum UiTheme {
    NEON(0xFF1B0B3A, 0xFF0B3A4A, 0xFF00F0FF),
    SUNSET(0xFF3A0B1B, 0xFF3A230B, 0xFFFF7A3D),
    OCEAN(0xFF081B3A, 0xFF0B3A33, 0xFF3DA5FF),
    FOREST(0xFF0B2A12, 0xFF1B2A0B, 0xFF5CFF7A),
    CANDY(0xFF3A0B33, 0xFF2A0B3A, 0xFFFF5CD6),
    MIDNIGHT(0xFF0A0A0F, 0xFF16161F, 0xFFB0B8FF);

    public final int top, bottom, accent;

    UiTheme(int top, int bottom, int accent) {
        this.top = top;
        this.bottom = bottom;
        this.accent = accent;
    }

    public Text label() { return Tr.t("patrickszcustoms.theme." + name().toLowerCase()); }
}
