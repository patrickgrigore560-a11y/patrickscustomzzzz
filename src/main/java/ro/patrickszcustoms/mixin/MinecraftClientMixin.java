package ro.patrickszcustoms.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ro.patrickszcustoms.animation.ScreenAnimator;

/** Detects every screen open / close / switch. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void patrickszcustoms$onSetScreen(Screen next, CallbackInfo ci) {
        ScreenAnimator.onScreenChange(((MinecraftClient) (Object) this).currentScreen, next);
    }
}
