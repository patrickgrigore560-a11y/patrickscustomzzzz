package ro.patrickszcustoms.config;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.text.Text;

public enum CrosshairType {
    DEFAULT, DOT, CIRCLE, CLASSIC, CROSS_DOT, SQUARE, T_SHAPE, X_SHAPE, RING_DOT, CUSTOM;

    public Text label() {
        return Tr.t("patrickszcustoms.crosshair." + name().toLowerCase());
    }
}
