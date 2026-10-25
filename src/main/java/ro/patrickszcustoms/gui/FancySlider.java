package ro.patrickszcustoms.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import ro.patrickszcustoms.animation.ScreenAnimator;

import java.util.function.Consumer;
import java.util.function.Function;

/** Neon slider: real range [min,max], custom label, change callback, section-switch animation. */
public class FancySlider extends SliderWidget {
    private final double min, max;
    private final Function<Double, Text> label;
    private final Consumer<Double> onChange;
    private float hover;
    private long last;

    public FancySlider(int x, int y, int w, int h, double min, double max, double current,
                       Function<Double, Text> label, Consumer<Double> onChange) {
        super(x, y, w, h, Text.empty(), (current - min) / (max - min));
        this.min = min;
        this.max = max;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    private double real() { return min + (max - min) * this.value; }

    @Override protected void updateMessage() { setMessage(label.apply(real())); }

    @Override protected void applyValue() {
        onChange.accept(real());
        UiSounds.tick(this.value);
    }

    /** Sliders tick while dragging instead of clicking on press. */
    @Override
    public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) { }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = last == 0L ? 0f : (now - last) / 1000f;
        last = now;
        hover += ((isHovered() ? 1f : 0f) - hover) * Math.min(1f, dt * 16f);

        float t = Ui.contentT;
        float outerAlpha = ScreenAnimator.getAlpha();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((1f - t) * 28f * Ui.contentDir, 0f);
        ScreenAnimator.setAlpha(outerAlpha * t);

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int acc = Ui.accent() | 0xFF000000;
        Ui.rect(ctx, x, y, w, h, Ui.mix(0x33101018, Ui.alpha(acc, 0.18f), hover));

        int fillW = (int) Math.round((w - 2) * this.value);
        if (fillW > 0) ctx.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, Ui.alpha(acc, 0.35f + 0.15f * hover));
        int knobX = x + 1 + Math.max(0, Math.min(w - 5, fillW - 2));
        ctx.fill(knobX, y, knobX + 3, y + h, acc);

        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);

        ctx.getMatrices().popMatrix();
        ScreenAnimator.setAlpha(outerAlpha);
    }
}
