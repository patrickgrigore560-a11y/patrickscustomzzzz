package ro.patrickszcustoms.config;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.text.Text;

public enum HudPos {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    public Text label() { return Tr.t("patrickszcustoms.pos." + name().toLowerCase()); }
}
