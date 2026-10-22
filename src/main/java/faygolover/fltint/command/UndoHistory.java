package faygolover.fltint.command;

import faygolover.fltint.Config;
import faygolover.fltint.data.Shades;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Последние покраски и правки защиты игрока (валик, //shade set|clear, //protect set|clear) для undo. Только в памяти, до выхода. */
public final class UndoHistory {
    private static final int DEPTH = 5;

    /** protect: old — 1/0 (была ли защита), иначе — прежний цвет. */
    private record Op(ResourceKey<Level> dim, boolean protect, long[] pos, int[] old) {}

    private static final Map<UUID, Deque<Op>> HISTORY = new HashMap<>();

    private UndoHistory() {}

    public static Recorder begin(@Nullable ServerPlayer player, ServerLevel level) {
        return new Recorder(player, level.dimension(), false);
    }

    public static Recorder beginProtect(@Nullable ServerPlayer player, ServerLevel level) {
        return new Recorder(player, level.dimension(), true);
    }

    public static final class Recorder {
        private final ServerPlayer player;
        private final ResourceKey<Level> dim;
        private final boolean protect;
        private final LongArrayList pos = new LongArrayList();
        private final IntArrayList old = new IntArrayList();
        private boolean overflow;

        private Recorder(@Nullable ServerPlayer player, ResourceKey<Level> dim, boolean protect) {
            this.player = player;
            this.dim = dim;
            this.protect = protect;
        }

        public void add(BlockPos p, int oldColor) {
            if (player == null || overflow) return;
            if (pos.size() >= Config.MAX_UNDO_BLOCKS.get()) {
                overflow = true;
                pos.clear();
                old.clear();
                return;
            }
            pos.add(p.asLong());
            old.add(oldColor);
        }

        /** true — операция сохранена для отмены. */
        public boolean commit() {
            if (player == null || overflow || pos.isEmpty()) return false;
            Deque<Op> deque = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
            deque.push(new Op(dim, protect, pos.toLongArray(), old.toIntArray()));
            while (deque.size() > DEPTH) deque.removeLast();
            return true;
        }

        public boolean overflowed() {
            return overflow;
        }
    }

    /** Откатывает последнюю операцию. -1 — нечего откатывать. */
    public static int undo(ServerPlayer player) {
        Deque<Op> deque = HISTORY.get(player.getUUID());
        if (deque == null || deque.isEmpty()) return -1;
        Op op = deque.pop();
        ServerLevel level = player.server.getLevel(op.dim);
        if (level == null) return 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = op.pos.length - 1; i >= 0; i--) {
            p.set(op.pos[i]);
            if (op.protect) Shades.setProtected(level, p, op.old[i] != 0);
            else Shades.set(level, p, op.old[i]);
        }
        return op.pos.length;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HISTORY.remove(event.getEntity().getUUID());
    }
}
