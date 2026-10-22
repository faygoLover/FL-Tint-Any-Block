package faygolover.fltint.net;

import faygolover.fltint.FlTint;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlTint.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        CHANNEL.messageBuilder(ShadeSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShadeSyncPacket::encode)
                .decoder(ShadeSyncPacket::decode)
                .consumerNetworkThread(ShadeSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(BrushConfigPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BrushConfigPacket::encode)
                .decoder(BrushConfigPacket::decode)
                .consumerNetworkThread(BrushConfigPacket::handle)
                .add();
    }
}
