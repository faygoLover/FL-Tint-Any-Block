package faygolover.fltint.compat;

import faygolover.fltint.ShadeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * SecurityCraft без зависимости при сборке: всё через рефлексию по его открытому API.
 * IReinforcedBlock.convertToVanilla — сам SC переносит свойства (поворот, половинки, waterlogged).
 * Оттенок — как в ClientHandler.initTint у SC: аннотация @Reinforced(hasReinforcedTint, customTint) на полях SCContent.
 */
public final class SecurityCraftCompat {
    private static final Logger LOG = LoggerFactory.getLogger("fltint");

    private static boolean initDone;
    private static Class<?> reinforcedIface;
    private static Method convertToVanilla;
    /** Блок → {hasReinforcedTint ? 1 : 0, customTint}. Блоков без аннотации тут нет — у них оттенок по умолчанию. */
    private static final Map<Block, int[]> TINTS = new IdentityHashMap<>();

    private SecurityCraftCompat() {}

    public static boolean available() {
        init();
        return convertToVanilla != null;
    }

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        if (!ModList.get().isLoaded("securitycraft")) return;
        try {
            reinforcedIface = Class.forName("net.geforcemods.securitycraft.api.IReinforcedBlock");
            convertToVanilla = reinforcedIface.getMethod("convertToVanilla", Level.class, BlockPos.class, BlockState.class);
        } catch (ReflectiveOperationException e) {
            LOG.warn("[fltint] SecurityCraft: API не найдено, /shade convert недоступна", e);
            reinforcedIface = null;
            convertToVanilla = null;
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> ann = (Class<? extends Annotation>) Class.forName("net.geforcemods.securitycraft.util.Reinforced");
            Method hasTint = ann.getMethod("hasReinforcedTint");
            Method customTint = ann.getMethod("customTint");
            for (Field f : Class.forName("net.geforcemods.securitycraft.SCContent").getFields()) {
                Annotation a = f.getAnnotation(ann);
                if (a == null || !(f.get(null) instanceof RegistryObject<?> ro) || !ro.isPresent() || !(ro.get() instanceof Block block)) continue;
                TINTS.put(block, new int[]{(Boolean) hasTint.invoke(a) ? 1 : 0, (Integer) customTint.invoke(a)});
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[fltint] SecurityCraft: оттенки блоков не прочитаны, всем будет оттенок по умолчанию", e);
        }
    }

    public static boolean isReinforced(Block block) {
        init();
        return reinforcedIface != null && reinforcedIface.isInstance(block);
    }

    /** Ванильное состояние для укреплённого блока или null. */
    public static BlockState toVanilla(Level level, BlockPos pos, BlockState state) {
        if (!isReinforced(state.getBlock())) return null;
        try {
            return (BlockState) convertToVanilla.invoke(state.getBlock(), level, pos, state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Оттенок, с которым укреплённый блок выглядит у SC: customTint × reinforcedTint (если у блока он включён).
     * {@code reinforcedTint} — reinforced_block_tint_color, у нас по умолчанию {@link ShadeColors#REINFORCED}.
     */
    public static int shadeFor(Block block, int reinforcedTint) {
        int[] t = TINTS.get(block);
        if (t == null) return reinforcedTint & 0xFFFFFF;
        int custom = t[1] & 0xFFFFFF;
        return t[0] == 1 ? ShadeColors.multiply(custom, reinforcedTint) : custom;
    }
}
