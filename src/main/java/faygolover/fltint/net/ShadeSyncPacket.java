package faygolover.fltint.net;

import faygolover.fltint.data.ShadeData;
import faygolover.fltint.data.ShadeSection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Оттенки чанка сервер → клиент. full = все секции чанка (лишние на клиенте убираются),
 * иначе только перечисленные; null-секция = в ней больше нет оттенков.
 * Секции копируются при создании пакета: кодирование идёт в сетевом потоке.
 */
public final class ShadeSyncPacket {
    public final int chunkX;
    public final int chunkZ;
    public final boolean full;
    public final Int2ObjectMap<ShadeSection> sections;

    private ShadeSyncPacket(int chunkX, int chunkZ, boolean full, Int2ObjectMap<ShadeSection> sections) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.full = full;
        this.sections = sections;
    }

    public static ShadeSyncPacket full(LevelChunk chunk, ShadeData data) {
        Int2ObjectMap<ShadeSection> map = new Int2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<ShadeSection> e : data.sections().int2ObjectEntrySet()) map.put(e.getIntKey(), e.getValue().copy());
        return new ShadeSyncPacket(chunk.getPos().x, chunk.getPos().z, true, map);
    }

    public static ShadeSyncPacket sections(LevelChunk chunk, ShadeData data, IntList ys) {
        Int2ObjectMap<ShadeSection> map = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < ys.size(); i++) {
            int y = ys.getInt(i);
            ShadeSection s = data == null ? null : data.section(y);
            map.put(y, s == null || s.isEmpty() ? null : s.copy());
        }
        return new ShadeSyncPacket(chunk.getPos().x, chunk.getPos().z, false, map);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeBoolean(full);
        buf.writeVarInt(sections.size());
        for (Int2ObjectMap.Entry<ShadeSection> e : sections.int2ObjectEntrySet()) {
            buf.writeVarInt(e.getIntKey());
            ShadeSection s = e.getValue();
            buf.writeBoolean(s != null);
            if (s != null) s.write(buf);
        }
    }

    public static ShadeSyncPacket decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int z = buf.readInt();
        boolean full = buf.readBoolean();
        int n = buf.readVarInt();
        Int2ObjectMap<ShadeSection> map = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < n; i++) {
            int y = buf.readVarInt();
            map.put(y, buf.readBoolean() ? ShadeSection.read(buf) : null);
        }
        return new ShadeSyncPacket(x, z, full, map);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> faygolover.fltint.client.ShadeClient.apply(this)));
        ctx.get().setPacketHandled(true);
    }
}
