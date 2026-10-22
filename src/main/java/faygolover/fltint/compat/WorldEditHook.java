package faygolover.fltint.compat;

import com.sk89q.jnbt.ByteTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import faygolover.fltint.ShadeColors;
import faygolover.fltint.data.Shades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * WorldEdit: оттенок и защита едут вместе с блоком — как содержимое сундука, в NBT блока (ключи fltint:*).
 * Обёртка ставится ближе всего к миру (Stage.BEFORE_CHANGE), поэтому через неё идут и чтения для //copy, истории
 * (//undo, //redo), и все записи. Работают //copy //cut //paste //rotate //flip //stack //move //undo и схематики.
 * Записи WorldEdit обходят защиту (это инструмент ГМ). Класс грузится, только если WorldEdit есть.
 */
public final class WorldEditHook {
    private static final Logger LOG = LoggerFactory.getLogger("fltint");
    static final String KEY_COLOR = "fltint:color";
    static final String KEY_PROTECTED = "fltint:protected";

    private static boolean registered;

    private WorldEditHook() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        WorldEdit.getInstance().getEventBus().register(new WorldEditHook());
        LOG.info("[fltint] WorldEdit: оттенки и защита копируются вместе с блоками");
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE) return;
        World world = event.getWorld();
        if (world == null) return;
        ServerLevel level;
        try {
            level = ForgeAdapter.adapt(world);
        } catch (RuntimeException e) {
            return;
        }
        if (level == null) return;
        Actor actor = event.getActor();
        boolean keep = actor != null && actor.isPlayer() && WeMode.keep(actor.getUniqueId());
        event.setExtent(new ShadeExtent(event.getExtent(), level, keep));
    }

    private static final class ShadeExtent extends AbstractDelegateExtent {
        private final ServerLevel level;
        private final boolean keep;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        ShadeExtent(Extent extent, ServerLevel level, boolean keep) {
            super(extent);
            this.level = level;
            this.keep = keep;
        }

        @Override
        public BaseBlock getFullBlock(BlockVector3 location) {
            BaseBlock block = super.getFullBlock(location);
            pos.set(location.getX(), location.getY(), location.getZ());
            int color = Shades.get(level, pos);
            boolean prot = Shades.isProtected(level, pos);
            if (color == ShadeColors.NONE && !prot) return block;
            Map<String, Tag> map = block.hasNbtData() ? new HashMap<>(block.getNbtData().getValue()) : new HashMap<>();
            if (color != ShadeColors.NONE) map.put(KEY_COLOR, new IntTag(color));
            if (prot) map.put(KEY_PROTECTED, new ByteTag((byte) 1));
            return block.toBaseBlock(new CompoundTag(map));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
            int color = ShadeColors.NONE;
            boolean prot = false;
            boolean carries = false;
            T toSet = block;
            if (block instanceof BaseBlock base && base.hasNbtData()) {
                CompoundTag tag = base.getNbtData();
                if (tag.containsKey(KEY_COLOR) || tag.containsKey(KEY_PROTECTED)) {
                    carries = true;
                    if (tag.containsKey(KEY_COLOR)) color = tag.getInt(KEY_COLOR) & 0xFFFFFF;
                    prot = tag.getByte(KEY_PROTECTED) != 0;
                    Map<String, Tag> rest = new HashMap<>(tag.getValue());
                    rest.remove(KEY_COLOR);
                    rest.remove(KEY_PROTECTED);
                    // Наши ключи миру не отдаём: у обычного блока NBT быть не должно, у сундука — только его собственный.
                    toSet = (T) (rest.isEmpty() ? base.toImmutableState().toBaseBlock() : base.toBaseBlock(new CompoundTag(rest)));
                }
            }
            boolean result;
            Shades.enterBypass();
            try {
                result = super.setBlock(location, toSet);
            } finally {
                Shades.exitBypass();
            }
            pos.set(location.getX(), location.getY(), location.getZ());
            if (carries || !keep) {
                Shades.set(level, pos, color);
                Shades.setProtected(level, pos, prot);
            }
            return result;
        }
    }
}
