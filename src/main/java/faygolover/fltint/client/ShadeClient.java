package faygolover.fltint.client;

import faygolover.fltint.ShadeColors;
import faygolover.fltint.data.ShadeSection;
import faygolover.fltint.net.ShadeSyncPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Оттенки и защита на клиенте: SectionPos.asLong → неизменяемая секция. Карта целиком заменяется при каждом изменении
 * (копия при записи): пишет только главный поток, а потоки сборки мешей Embeddium читают без блокировок.
 * Изменения редки (загрузка чанка с оттенками, покраска), чтения — на каждый квад.
 */
public final class ShadeClient {
    private static volatile Long2ObjectOpenHashMap<ShadeSection> sections = new Long2ObjectOpenHashMap<>();

    private ShadeClient() {}

    public static int get(BlockPos pos) {
        Long2ObjectOpenHashMap<ShadeSection> map = sections;
        if (map.isEmpty()) return ShadeColors.NONE;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        ShadeSection s = map.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        return s == null ? ShadeColors.NONE : s.get(ShadeSection.localIndex(x, y, z));
    }

    public static boolean isProtected(BlockPos pos) {
        Long2ObjectOpenHashMap<ShadeSection> map = sections;
        if (map.isEmpty()) return false;
        ShadeSection s = map.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        return s != null && s.isProtected(ShadeSection.localIndex(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** Все секции (для подсветки защиты). Снимок, не меняется. */
    static Long2ObjectOpenHashMap<ShadeSection> snapshot() {
        return sections;
    }

    /** Пакет с сервера (главный поток). */
    public static void apply(ShadeSyncPacket packet) {
        Long2ObjectOpenHashMap<ShadeSection> map = new Long2ObjectOpenHashMap<>(sections);
        LongArrayList touched = new LongArrayList();
        if (packet.full) {
            var it = map.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                long key = it.next().getLongKey();
                if (SectionPos.x(key) == packet.chunkX && SectionPos.z(key) == packet.chunkZ) {
                    touched.add(key);
                    it.remove();
                }
            }
        }
        for (Int2ObjectMap.Entry<ShadeSection> e : packet.sections.int2ObjectEntrySet()) {
            long key = SectionPos.asLong(packet.chunkX, e.getIntKey(), packet.chunkZ);
            ShadeSection s = e.getValue();
            if (s == null || s.isEmpty()) map.remove(key);
            else map.put(key, s);
            touched.add(key);
        }
        sections = map;
        rebuild(touched);
    }

    /** Блок на клиенте стал воздухом — убрать оттенок и защиту сразу, не дожидаясь сервера. */
    public static void onBlockGone(BlockPos pos) {
        if (get(pos) == ShadeColors.NONE && !isProtected(pos)) return;
        long key = SectionPos.asLong(pos);
        Long2ObjectOpenHashMap<ShadeSection> map = new Long2ObjectOpenHashMap<>(sections);
        ShadeSection s = map.get(key).copy();
        int local = ShadeSection.localIndex(pos.getX(), pos.getY(), pos.getZ());
        s.set(local, ShadeColors.NONE);
        s.setProtected(local, false);
        if (s.isEmpty()) map.remove(key);
        else map.put(key, s);
        sections = map;
    }

    public static void removeChunk(int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<ShadeSection> current = sections;
        if (current.isEmpty()) return;
        Long2ObjectOpenHashMap<ShadeSection> map = null;
        for (long key : current.keySet()) {
            if (SectionPos.x(key) == chunkX && SectionPos.z(key) == chunkZ) {
                if (map == null) map = new Long2ObjectOpenHashMap<>(current);
                map.remove(key);
            }
        }
        if (map != null) sections = map;
    }

    public static void clear() {
        sections = new Long2ObjectOpenHashMap<>();
    }

    private static void rebuild(LongArrayList keys) {
        var renderer = Minecraft.getInstance().levelRenderer;
        for (int i = 0; i < keys.size(); i++) {
            long k = keys.getLong(i);
            renderer.setSectionDirty(SectionPos.x(k), SectionPos.y(k), SectionPos.z(k));
        }
    }
}
