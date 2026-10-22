package faygolover.fltint.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Защищённые блоки: ломает только ГМ (оператор 2+). Не берут взрывы, поршни, мобы.
 * Последний рубеж — LevelChunkMixin/LevelMixin (огонь, моды, всё остальное); здесь — то, что нужно сделать
 * «по-хорошему»: без трещин у игрока, без дюпа дропа при взрыве, без полусдвинутых поршнем конструкций.
 */
public final class ProtectionEvents {
    private ProtectionEvents() {}

    public static boolean isGm(Player player) {
        return player.hasPermissions(2);
    }

    /** Не-ГМ даже не начинает ломать: скорость 0 — нет трещин и «призрачных» блоков (обе стороны). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isGm(event.getEntity())) return;
        event.getPosition().ifPresent(pos -> {
            if (Shades.isProtected(event.getEntity().level(), pos)) event.setNewSpeed(0f);
        });
    }

    /** Креатив ломает мгновенно, мимо скорости — отбиваем сам клик (обе стороны). */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.isCreative() && !isGm(player) && Shades.isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!isGm(event.getPlayer()) && Shades.isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    /** ГМ ломает защищённый блок: снимаем защиту, если слом никто не отменил (иначе LevelChunkMixin не даст убрать блок). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGmBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (isGm(event.getPlayer()) && Shades.isProtected(level, event.getPos())) Shades.setProtected(level, event.getPos(), false);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;
        event.getAffectedBlocks().removeIf(pos -> Shades.isProtected(event.getLevel(), pos));
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        LevelAccessor level = event.getLevel();
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null) return;
        resolver.resolve();
        for (BlockPos pos : resolver.getToPush()) {
            if (Shades.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (Shades.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Визер, дракон, разоритель и прочие мобы, ломающие блоки через Forge. */
    @SubscribeEvent
    public static void onMobDestroy(LivingDestroyBlockEvent event) {
        if (Shades.isProtected(event.getEntity().level(), event.getPos())) event.setCanceled(true);
    }
}
