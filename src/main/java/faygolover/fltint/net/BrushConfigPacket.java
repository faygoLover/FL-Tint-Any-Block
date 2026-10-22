package faygolover.fltint.net;

import faygolover.fltint.item.BrushItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Окно валика → сервер: новые цвет, режим, радиус, «только такой же блок». */
public record BrushConfigPacket(InteractionHand hand, int color, BrushItem.Mode mode, int radius, boolean sameBlock) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeInt(color);
        buf.writeEnum(mode);
        buf.writeVarInt(radius);
        buf.writeBoolean(sameBlock);
    }

    public static BrushConfigPacket decode(FriendlyByteBuf buf) {
        return new BrushConfigPacket(buf.readEnum(InteractionHand.class), buf.readInt(), buf.readEnum(BrushItem.Mode.class),
                buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !BrushItem.canUse(player)) return;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BrushItem) BrushItem.configure(stack, color, mode, radius, sameBlock);
        });
        ctx.get().setPacketHandled(true);
    }
}
