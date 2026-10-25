package ro.patrickszcustoms.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.CrosshairType;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.render.CrosshairManager;
import ro.patrickszcustoms.render.CrosshairRenderer;

/** Replaces the vanilla crosshair. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void patrickszcustoms$renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.crosshairType == CrosshairType.DEFAULT) return; // vanilla
        GameOptions options = MinecraftClient.getInstance().options;
        if (!options.getPerspective().isFirstPerson()) { ci.cancel(); return; }

        CrosshairRenderer.render(context,
                context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2,
                cfg, CrosshairManager.currentScale(), CrosshairManager.hitProgress());
        ci.cancel();
    }
}
