package ro.patrickszcustoms.mixin;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ro.patrickszcustoms.animation.ScreenAnimator;

/**
 * Fade support for 1.21.6+: there is no global shader color anymore, so we multiply the
 * ARGB alpha at the "leaf" draw methods (the ones that finally create render-state elements).
 * require = 0: if a signature ever changes, fade just degrades instead of crashing the game.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {

    // fill(...) / fillGradient(...) leaf
    @ModifyVariable(
            method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private int patrickszcustoms$fillColor(int color) {
        return ScreenAnimator.modulate(color);
    }

    @ModifyVariable(
            method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Integer patrickszcustoms$fillColor2(Integer color) {
        return color == null ? null : ScreenAnimator.modulate(color);
    }

    // textured quad leaf (sprites, buttons, slots...)
    @ModifyVariable(
            method = "drawTexturedQuad(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lnet/minecraft/client/gl/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private int patrickszcustoms$quadColor(int color) {
        return ScreenAnimator.modulate(color);
    }

    // tiled textured quad leaf
    @ModifyVariable(
            method = "drawTiledTexturedQuad(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lnet/minecraft/client/gl/GpuSampler;IIIIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 6, require = 0)
    private int patrickszcustoms$tiledColor(int color) {
        return ScreenAnimator.modulate(color);
    }

    // text (drawTextWithShadow / drawCenteredTextWithShadow delegate to drawText)
    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
            },
            at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private int patrickszcustoms$textColor(int color) {
        return ScreenAnimator.modulate(color);
    }
}
