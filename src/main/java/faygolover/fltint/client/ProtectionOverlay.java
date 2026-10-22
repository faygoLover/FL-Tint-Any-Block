package faygolover.fltint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.fltint.data.ShadeSection;
import faygolover.fltint.item.BrushItem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;

/**
 * ГМ с валиком в руке видит контуры защищённых блоков в радиусе 12 (только открытые — те, что не закрыты со всех
 * сторон). Список пересчитывается раз в полсекунды, рисуется каждый кадр.
 */
public final class ProtectionOverlay {
    private static final int RADIUS = 12;
    private static final int MAX_BOXES = 4096;

    private static LongArrayList boxes = new LongArrayList();
    private static int tick;

    private ProtectionOverlay() {}

    static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !holdsBrush(player) || !BrushItem.canUse(player)) {
            if (!boxes.isEmpty()) boxes = new LongArrayList();
            return;
        }
        if (++tick % 10 == 0) boxes = collect(mc.level, player.blockPosition());
    }

    static boolean holdsBrush(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof BrushItem || player.getOffhandItem().getItem() instanceof BrushItem;
    }

    private static LongArrayList collect(ClientLevel level, BlockPos center) {
        LongArrayList out = new LongArrayList();
        int r2 = RADIUS * RADIUS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<ShadeSection> e : ShadeClient.snapshot().long2ObjectEntrySet()) {
            ShadeSection s = e.getValue();
            if (!s.hasProtection()) continue;
            long key = e.getLongKey();
            int bx = SectionPos.x(key) << 4, by = SectionPos.y(key) << 4, bz = SectionPos.z(key) << 4;
            if (Math.abs(bx + 8 - center.getX()) > RADIUS + 8 || Math.abs(by + 8 - center.getY()) > RADIUS + 8
                    || Math.abs(bz + 8 - center.getZ()) > RADIUS + 8) continue;
            for (int local = 0; local < 4096; local++) {
                if (!s.isProtected(local)) continue;
                p.set(bx + (local & 15), by + (local >> 8), bz + (local >> 4 & 15));
                if (p.distSqr(center) > r2) continue;
                boolean exposed = false;
                for (Direction d : Direction.values()) {
                    n.setWithOffset(p, d);
                    if (!level.getBlockState(n).isSolidRender(level, n)) {
                        exposed = true;
                        break;
                    }
                }
                if (!exposed) continue;
                out.add(p.asLong());
                if (out.size() >= MAX_BOXES) return out;
            }
        }
        return out;
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        LongArrayList list = boxes;
        if (list.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < list.size(); i++) {
            p.set(list.getLong(i));
            double x = p.getX() - cam.x, y = p.getY() - cam.y, z = p.getZ() - cam.z;
            LevelRenderer.renderLineBox(pose, lines, x - 0.002, y - 0.002, z - 0.002, x + 1.002, y + 1.002, z + 1.002,
                    1.0f, 0.55f, 0.1f, 0.85f);
        }
        buffers.endBatch(RenderType.lines());
    }
}
