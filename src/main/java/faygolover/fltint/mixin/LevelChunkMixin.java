package faygolover.fltint.mixin;

import faygolover.fltint.data.ShadeChunk;
import faygolover.fltint.data.ShadeData;
import faygolover.fltint.data.Shades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Оттенки и защита чанка — обычное поле (без capability: быстрее в setBlockState, сохраняется через ChunkDataEvent).
 * <p>
 * Защита: защищённый блок нельзя сменить на другой блок ничем (игрок, огонь, моды, мобы) — кроме правок
 * WorldEdit/наших команд ({@link Shades#bypassing()}); ГМ при сломе сначала снимает защиту (ProtectionEvents).
 * Смена состояния того же блока (дверь, печь, вода в блоке) разрешена.
 * <p>
 * Блок стал воздухом — оттенок и защита с позиции снимаются. Смена блока на другой блок оттенок не снимает.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ShadeChunk {
    @Shadow @Final Level level;

    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Unique private ShadeData fltint$data;

    @Override
    public ShadeData fltint$data() {
        return fltint$data;
    }

    @Override
    public void fltint$setData(ShadeData data) {
        fltint$data = data;
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void fltint$guard(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (fltint$data == null || level.isClientSide || Shades.bypassing()) return;
        if (!fltint$data.isProtected(pos.getX(), pos.getY(), pos.getZ())) return;
        if (getBlockState(pos).getBlock() != state.getBlock()) cir.setReturnValue(null);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void fltint$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() == null || !state.isAir()) return;
        if (level.isClientSide) Shades.clientBlockGone.accept(pos);
        else if (fltint$data != null) Shades.onBlockGone((LevelChunk) (Object) this, pos);
    }
}
