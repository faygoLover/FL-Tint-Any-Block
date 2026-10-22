package faygolover.fltint.client;

import faygolover.fltint.FlTint;
import faygolover.fltint.ShadeColors;
import faygolover.fltint.compat.SecurityCraftCompat;
import faygolover.fltint.data.Shades;
import faygolover.fltint.item.BrushItem;
import faygolover.fltint.net.BrushConfigPacket;
import faygolover.fltint.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        Shades.clientBlockGone = ShadeClient::onBlockGone;
        Shades.clientProtected = ShadeClient::isProtected;
        modBus.addListener(ClientSetup::itemColors);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::chunkUnload);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::levelUnload);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::loggingOut);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::pickBlock);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::debugText);
        MinecraftForge.EVENT_BUS.addListener(ProtectionOverlay::clientTick);
        MinecraftForge.EVENT_BUS.addListener(ProtectionOverlay::render);
    }

    /** Слой 1 модели валика (краска на ролике) = текущий цвет; ластик — белый. */
    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 1 ? 0xFF000000 | BrushItem.color(stack) : -1, FlTint.BRUSH.get());
    }

    private static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) ShadeClient.removeChunk(event.getChunk().getPos().x, event.getChunk().getPos().z);
    }

    private static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ShadeClient.clear();
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ShadeClient.clear();
    }

    /** Колёсико с валиком в руке — пипетка (как Shift+ПКМ), вместо «выбрать блок». */
    private static void pickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        InteractionHand hand = player.getMainHandItem().getItem() instanceof BrushItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof BrushItem ? InteractionHand.OFF_HAND : null;
        if (hand == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!BrushItem.canUse(player)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        int color = ShadeClient.get(pos);
        if (color == ShadeColors.NONE && SecurityCraftCompat.isReinforced(state.getBlock())) {
            color = SecurityCraftCompat.shadeFor(state.getBlock(), ShadeColors.REINFORCED);
        }
        ItemStack stack = player.getItemInHand(hand);
        BrushItem.Mode mode = BrushItem.mode(stack);
        int radius = BrushItem.radius(stack);
        boolean same = BrushItem.sameBlock(stack);
        Net.CHANNEL.sendToServer(new BrushConfigPacket(hand, color, mode, radius, same));
        BrushItem.configure(stack, color, mode, radius, same);
        player.displayClientMessage(Component.translatable("fltint.msg.picked", BrushItem.swatch(color)), true);
    }

    /** F3: оттенок и защита блока под прицелом. */
    private static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        int color = ShadeClient.get(pos);
        boolean prot = ShadeClient.isProtected(pos);
        if (color == ShadeColors.NONE && !prot) return;
        event.getRight().add("");
        event.getRight().add("fltint: " + (color == ShadeColors.NONE ? "-" : "#" + ShadeColors.hex(color)) + (prot ? ", protected" : ""));
    }

    static int previewColor(int color) {
        return color == ShadeColors.NONE ? 0xFFFFFFFF : 0xFF000000 | color;
    }
}
