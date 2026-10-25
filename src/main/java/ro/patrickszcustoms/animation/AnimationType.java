package ro.patrickszcustoms.animation;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.text.Text;

public enum AnimationType {
    FADE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN, ZOOM_IN, ZOOM_OUT;

    public Text label() {
        return Tr.t("patrickszcustoms.animation." + name().toLowerCase());
    }
}
