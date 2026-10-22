package faygolover.fltint.mixin;

import faygolover.fltint.data.Shades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * destroyBlock сначала роняет дроп, потом ставит воздух: защита в LevelChunk не дала бы сменить блок,
 * но дроп уже выпал бы (дюп). Поэтому защищённый блок отбивается здесь же, до дропа.
 * Ломают так моды (оружие, буры), мобы, падающие блоки; игрок ломает через removeBlock.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void fltint$guard(BlockPos pos, boolean drop, Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide && !Shades.bypassing() && Shades.isProtected(self, pos)) cir.setReturnValue(false);
    }
}
