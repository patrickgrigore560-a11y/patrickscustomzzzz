package ro.patrickszcustoms.animation;

import net.minecraft.text.Text;
import ro.patrickszcustoms.i18n.Tr;

/** Easing curves for menu transitions. out() = opening, in() = closing. May overshoot (BACK, ELASTIC). */
public enum Easing {
    LINEAR, SINE, CUBIC, BACK, ELASTIC, BOUNCE;

    public Text label() { return Tr.t("patrickszcustoms.easing." + name().toLowerCase()); }

    public float out(float p) {
        p = Math.max(0f, Math.min(1f, p));
        switch (this) {
            case LINEAR: return p;
            case SINE: return (float) Math.sin(p * Math.PI / 2.0);
            case BACK: { float c1 = 1.70158f, c3 = c1 + 1f, q = p - 1f; return 1f + c3 * q * q * q + c1 * q * q; }
            case ELASTIC: {
                if (p == 0f || p == 1f) return p;
                return (float) (Math.pow(2, -10 * p) * Math.sin((p * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
            }
            case BOUNCE: return bounceOut(p);
            default: { float i = 1f - p; return 1f - i * i * i; } // CUBIC
        }
    }

    public float in(float p) {
        p = Math.max(0f, Math.min(1f, p));
        switch (this) {
            case LINEAR: return p;
            case SINE: return 1f - (float) Math.cos(p * Math.PI / 2.0);
            case BACK: { float c1 = 1.70158f, c3 = c1 + 1f; return c3 * p * p * p - c1 * p * p; }
            case ELASTIC: {
                if (p == 0f || p == 1f) return p;
                return (float) (-Math.pow(2, 10 * p - 10) * Math.sin((p * 10 - 10.75) * (2 * Math.PI / 3)));
            }
            case BOUNCE: return 1f - bounceOut(1f - p);
            default: return p * p * p; // CUBIC
        }
    }

    private static float bounceOut(float x) {
        float n1 = 7.5625f, d1 = 2.75f;
        if (x < 1f / d1) return n1 * x * x;
        if (x < 2f / d1) { x -= 1.5f / d1; return n1 * x * x + 0.75f; }
        if (x < 2.5f / d1) { x -= 2.25f / d1; return n1 * x * x + 0.9375f; }
        x -= 2.625f / d1;
        return n1 * x * x + 0.984375f;
    }
}
