package faygolover.fltint.mixin.client;

import faygolover.fltint.ShadeColors;
import faygolover.fltint.client.ShadeClient;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium: цвета вершин квада (ABGR) считаются здесь для каждого квада блока, тинт или нет.
 * Умножаем их на оттенок позиции — дальше Embeddium смешивает их с освещением как обычно.
 * Работает в потоках сборки мешей: ShadeClient читается без блокировок.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class BlockRendererEmbeddiumMixin {
    @Inject(method = "getVertexColors", at = @At("RETURN"), remap = false)
    private void fltint$shade(BlockRenderContext ctx, ColorProvider<BlockState> provider, BakedQuadView quad,
                               CallbackInfoReturnable<int[]> cir) {
        int shade = ShadeClient.get(ctx.pos());
        if (shade == ShadeColors.NONE) return;
        int[] colors = cir.getReturnValue();
        for (int i = 0; i < colors.length; i++) colors[i] = ShadeColors.applyToAbgr(colors[i], shade);
    }
}
