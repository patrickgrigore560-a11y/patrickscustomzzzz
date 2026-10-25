package ro.patrickszcustoms.gui;

import ro.patrickszcustoms.i18n.Tr;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Trivial screen used to test open / close animations from the config menu. */
public class AnimationTestScreen extends Screen {
    private final Screen parent;

    public AnimationTestScreen(Screen parent) {
        super(Tr.t("patrickszcustoms.test.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Tr.t("patrickszcustoms.test.close"), b -> close())
                .dimensions(width / 2 - 60, height / 2 + 10, 120, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 20, 0xFFFFFFFF);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
