package ro.patrickszcustoms.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ro.patrickszcustoms.animation.ScreenAnimator;

/** Wraps the full render of any screen (Screen, HandledScreen, chests, settings...). */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void patrickszcustoms$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenAnimator.begin(context, (Screen) (Object) this, delta);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void patrickszcustoms$end(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenAnimator.end(context);
    }
}
