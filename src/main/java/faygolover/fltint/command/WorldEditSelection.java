package faygolover.fltint.command;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Выделение WorldEdit игрока. Класс грузится, только если WorldEdit есть (проверка в ShadeCommand). */
final class WorldEditSelection {
    private WorldEditSelection() {}

    /** Выделение в текущем мире игрока; иначе исключение с понятным текстом. */
    static Area get(ServerPlayer player) throws SelectionException {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(ForgeAdapter.adaptPlayer(player));
        World world = session.getSelectionWorld();
        if (world == null) throw new SelectionException(Component.translatable("fltint.cmd.no_selection"));
        if (ForgeAdapter.adapt(world) != player.serverLevel()) throw new SelectionException(Component.translatable("fltint.cmd.other_world"));
        Region region;
        try {
            region = session.getSelection(world);
        } catch (IncompleteRegionException e) {
            throw new SelectionException(Component.translatable("fltint.cmd.no_selection"));
        }
        return new Area() {
            @Override
            public long volume() {
                return region.getVolume();
            }

            @Override
            public void forEach(Consumer<BlockPos> action) {
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (BlockVector3 v : region) action.accept(p.set(v.getX(), v.getY(), v.getZ()));
            }
        };
    }

    static final class SelectionException extends Exception {
        final Component message;

        SelectionException(Component message) {
            super(message.getString(), null, false, false);
            this.message = message;
        }
    }
}
