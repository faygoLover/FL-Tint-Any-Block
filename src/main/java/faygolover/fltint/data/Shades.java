package faygolover.fltint.data;

import faygolover.fltint.ShadeColors;
import faygolover.fltint.net.Net;
import faygolover.fltint.net.ShadeSyncPacket;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;

/** Серверное API оттенков и защиты + сохранение в чанк + рассылка изменений игрокам, которые видят чанк. */
public final class Shades {
    /** Ответ {@link #set}, если в секции кончилась палитра. */
    public static final int FAILED = Integer.MIN_VALUE;

    /** Изменённые секции (SectionPos.asLong) по измерениям; уходят клиентам в конце тика пачкой на чанк. */
    private static final Map<ResourceKey<Level>, LongSet> DIRTY = new HashMap<>();

    /** Клиентские обработчики (ставит ClientSetup); на выделенном сервере — заглушки. */
    public static java.util.function.Consumer<BlockPos> clientBlockGone = pos -> {};
    public static java.util.function.Predicate<BlockPos> clientProtected = pos -> false;

    /**
     * > 0 — защита не мешает менять блоки: идут правки WorldEdit или наши команды (только ГМ).
     * Только поток сервера.
     */
    private static int bypass;

    private Shades() {}

    public static boolean bypassing() {
        return bypass > 0;
    }

    public static void runBypassing(Runnable action) {
        bypass++;
        try {
            action.run();
        } finally {
            bypass--;
        }
    }

    /** Для кода с проверяемыми исключениями: enterBypass(); try { ... } finally { exitBypass(); } */
    public static void enterBypass() {
        bypass++;
    }

    public static void exitBypass() {
        bypass--;
    }

    private static ShadeData dataAt(Level level, BlockPos pos) {
        return ((ShadeChunk) level.getChunkAt(pos)).fltint$data();
    }

    public static int get(Level level, BlockPos pos) {
        ShadeData data = dataAt(level, pos);
        return data == null ? ShadeColors.NONE : data.get(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Защищён ли блок. Не грузит чанки; работает на обеих сторонах. */
    public static boolean isProtected(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) return clientProtected.test(pos);
        if (!(level instanceof Level l)) return false;
        LevelChunk chunk = l.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        ShadeData data = ((ShadeChunk) chunk).fltint$data();
        return data != null && data.isProtected(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Ставит оттенок ({@link ShadeColors#NONE} = снять). Возвращает прежний цвет или {@link #FAILED}. */
    public static int set(ServerLevel level, BlockPos pos, int color) {
        LevelChunk chunk = level.getChunkAt(pos);
        ShadeChunk holder = (ShadeChunk) chunk;
        ShadeData data = holder.fltint$data();
        int old = data == null ? ShadeColors.NONE : data.get(pos.getX(), pos.getY(), pos.getZ());
        color &= 0xFFFFFF;
        if (old == color) return old;
        // На воздухе не держим: иначе «прилипло» бы к блоку, поставленному сюда потом.
        if (color != ShadeColors.NONE && chunk.getBlockState(pos).isAir()) return old;
        if (data == null) {
            data = new ShadeData();
            holder.fltint$setData(data);
        }
        if (!data.set(pos.getX(), pos.getY(), pos.getZ(), color)) return FAILED;
        if (data.isEmpty()) holder.fltint$setData(null);
        chunk.setUnsaved(true);
        markDirty(level, pos);
        return old;
    }

    /** Ставит/снимает защиту. Возвращает прежнее значение. На воздух защита не ставится. */
    public static boolean setProtected(ServerLevel level, BlockPos pos, boolean value) {
        LevelChunk chunk = level.getChunkAt(pos);
        ShadeChunk holder = (ShadeChunk) chunk;
        ShadeData data = holder.fltint$data();
        boolean old = data != null && data.isProtected(pos.getX(), pos.getY(), pos.getZ());
        if (old == value) return old;
        if (value && chunk.getBlockState(pos).isAir()) return old;
        if (data == null) {
            data = new ShadeData();
            holder.fltint$setData(data);
        }
        data.setProtected(pos.getX(), pos.getY(), pos.getZ(), value);
        if (data.isEmpty()) holder.fltint$setData(null);
        chunk.setUnsaved(true);
        markDirty(level, pos);
        return old;
    }

    /** Блок на позиции стал воздухом (сломан ГМом, взорван, сдвинут) — снимаются оттенок и защита. Только сервер. */
    public static void onBlockGone(LevelChunk chunk, BlockPos pos) {
        ShadeChunk holder = (ShadeChunk) chunk;
        ShadeData data = holder.fltint$data();
        if (data == null || !data.clear(pos.getX(), pos.getY(), pos.getZ())) return;
        if (data.isEmpty()) holder.fltint$setData(null);
        chunk.setUnsaved(true);
        markDirty(chunk.getLevel(), pos);
    }

    private static void markDirty(Level level, BlockPos pos) {
        DIRTY.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(SectionPos.asLong(pos));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRTY.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            LongSet dirty = DIRTY.remove(level.dimension());
            if (dirty == null) continue;
            Long2ObjectMap<IntArrayList> byChunk = new Long2ObjectOpenHashMap<>();
            dirty.forEach((long sp) -> byChunk
                    .computeIfAbsent(ChunkPos.asLong(SectionPos.x(sp), SectionPos.z(sp)), k -> new IntArrayList())
                    .add(SectionPos.y(sp)));
            for (Long2ObjectMap.Entry<IntArrayList> e : byChunk.long2ObjectEntrySet()) {
                long cp = e.getLongKey();
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(cp), ChunkPos.getZ(cp));
                if (chunk == null) continue;
                Net.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                        ShadeSyncPacket.sections(chunk, ((ShadeChunk) chunk).fltint$data(), e.getValue()));
            }
        }
        DIRTY.clear();
    }

    /** Игроку отправили чанк — следом шлём его оттенки (тот же канал, порядок сохраняется). */
    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        LevelChunk chunk = event.getChunk();
        ShadeData data = ((ShadeChunk) chunk).fltint$data();
        if (data == null || data.isEmpty()) return;
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), ShadeSyncPacket.full(chunk, data));
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        LevelChunk chunk = levelChunk(event.getChunk());
        if (chunk == null) return;
        ShadeData data = ((ShadeChunk) chunk).fltint$data();
        if (data != null && !data.isEmpty()) event.getData().put(ShadeData.NBT_KEY, data.save());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        if (!event.getData().contains(ShadeData.NBT_KEY)) return;
        LevelChunk chunk = levelChunk(event.getChunk());
        if (chunk == null) return;
        ShadeData data = ShadeData.load(event.getData().getCompound(ShadeData.NBT_KEY));
        ((ShadeChunk) chunk).fltint$setData(data.isEmpty() ? null : data);
    }

    private static LevelChunk levelChunk(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk lc) return lc;
        if (chunk instanceof ImposterProtoChunk ip) return ip.getWrapped();
        return null;
    }
}
