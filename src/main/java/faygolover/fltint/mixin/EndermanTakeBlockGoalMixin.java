package faygolover.fltint.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import faygolover.fltint.data.Shades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Эндермен берёт блок в руки, не проверяя, удалось ли его убрать, — защищённый блок он бы «скопировал». */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fltint$notProtected(boolean holdable, @Local Level level, @Local BlockPos pos) {
        return holdable && !Shades.isProtected(level, pos);
    }
}
