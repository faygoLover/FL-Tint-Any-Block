package faygolover.fltint.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.fltint.ShadeColors;
import faygolover.fltint.client.ShadeClient;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ванильная сборка чанков (без Embeddium): тот же множитель на цвет квада. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @WrapOperation(method = "putQuadData", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[IIZ)V"))
    private void fltint$shade(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                               float r, float g, float b, int[] lightmap, int overlay, boolean readExistingColor,
                               Operation<Void> original, @Local(argsOnly = true) BlockPos pos) {
        int shade = ShadeClient.get(pos);
        if (shade != ShadeColors.NONE) {
            r *= ((shade >> 16) & 0xFF) / 255f;
            g *= ((shade >> 8) & 0xFF) / 255f;
            b *= (shade & 0xFF) / 255f;
        }
        original.call(consumer, pose, quad, brightness, r, g, b, lightmap, overlay, readExistingColor);
    }
}
