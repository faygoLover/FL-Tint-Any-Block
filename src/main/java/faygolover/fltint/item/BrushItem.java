package faygolover.fltint.item;

import faygolover.fltint.ShadeColors;
import faygolover.fltint.command.UndoHistory;
import faygolover.fltint.compat.SecurityCraftCompat;
import faygolover.fltint.data.Shades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Валик оттенка (только ГМ = оператор 2+ уровня).
 * ПКМ по блоку — покрасить по режиму; Shift+ПКМ по блоку — пипетка; ПКМ в воздух — окно настроек.
 */
public final class BrushItem extends Item {
    public enum Mode { BLOCK, FACE, RADIUS }

    public static final int MAX_RADIUS = 16;
    private static final int MAX_FACE_BLOCKS = 4096;

    public BrushItem(Properties props) {
        super(props);
    }

    public static boolean canUse(Player player) {
        return player.hasPermissions(2);
    }

    // ---- настройки в NBT ----

    public static int color(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Color") ? tag.getInt("Color") & 0xFFFFFF : ShadeColors.REINFORCED;
    }

    public static Mode mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int i = tag == null ? 0 : tag.getByte("Mode");
        return Mode.values()[Math.floorMod(i, Mode.values().length)];
    }

    public static int radius(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int r = tag != null && tag.contains("Radius") ? tag.getInt("Radius") : 3;
        return Math.max(1, Math.min(MAX_RADIUS, r));
    }

    public static boolean sameBlock(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains("Same") || tag.getBoolean("Same");
    }

    public static void configure(ItemStack stack, int color, Mode mode, int radius, boolean same) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("Color", color & 0xFFFFFF);
        tag.putByte("Mode", (byte) mode.ordinal());
        tag.putInt("Radius", Math.max(1, Math.min(MAX_RADIUS, radius)));
        tag.putBoolean("Same", same);
    }

    // ---- использование ----

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!canUse(player)) {
            if (!ctx.getLevel().isClientSide) player.displayClientMessage(Component.translatable("fltint.msg.gm_only"), true);
            return InteractionResult.FAIL;
        }
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (player.isShiftKeyDown()) {
            int c = Shades.get(level, pos);
            if (c == ShadeColors.NONE && SecurityCraftCompat.isReinforced(state.getBlock())) {
                c = SecurityCraftCompat.shadeFor(state.getBlock(), ShadeColors.REINFORCED);
            }
            configure(stack, c, mode(stack), radius(stack), sameBlock(stack));
            player.displayClientMessage(Component.translatable("fltint.msg.picked", swatch(c)), true);
            return InteractionResult.SUCCESS;
        }

        int color = color(stack);
        List<BlockPos> targets = collect(level, pos, ctx.getClickedFace(), mode(stack), radius(stack), sameBlock(stack));
        UndoHistory.Recorder undo = UndoHistory.begin((ServerPlayer) player, level);
        int changed = 0;
        int failed = 0;
        for (BlockPos p : targets) {
            int old = Shades.set(level, p, color);
            if (old == Shades.FAILED) failed++;
            else if (old != color) {
                changed++;
                undo.add(p, old);
            }
        }
        undo.commit();
        player.displayClientMessage(failed > 0
                ? Component.translatable("fltint.msg.painted_failed", changed, failed)
                : Component.translatable("fltint.msg.painted", changed), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("fltint.msg.gm_only"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.fltint.client.BrushScreen.open(hand, stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Какие блоки покрасит клик. Воздух не красится никогда. */
    static List<BlockPos> collect(Level level, BlockPos origin, Direction face, Mode mode, int radius, boolean same) {
        List<BlockPos> out = new ArrayList<>();
        BlockState originState = level.getBlockState(origin);
        if (originState.isAir()) return out;
        Block originBlock = originState.getBlock();
        switch (mode) {
            case BLOCK -> out.add(origin.immutable());
            case RADIUS -> {
                int r2 = radius * radius + radius;
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r2) continue;
                            p.setWithOffset(origin, dx, dy, dz);
                            BlockState s = level.getBlockState(p);
                            if (s.isAir() || same && s.getBlock() != originBlock) continue;
                            out.add(p.immutable());
                        }
                    }
                }
            }
            case FACE -> {
                // Заливка по плоскости грани: связные блоки, у которых эта грань открыта, в квадрате ±radius.
                Direction[] around = planeDirections(face.getAxis());
                Set<BlockPos> seen = new HashSet<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                BlockPos start = origin.immutable();
                queue.add(start);
                seen.add(start);
                while (!queue.isEmpty() && out.size() < MAX_FACE_BLOCKS) {
                    BlockPos p = queue.poll();
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || same && s.getBlock() != originBlock) continue;
                    BlockPos front = p.relative(face);
                    if (!p.equals(start) && level.getBlockState(front).isSolidRender(level, front)) continue;
                    out.add(p);
                    for (Direction d : around) {
                        BlockPos n = p.relative(d);
                        if (Math.abs(n.getX() - origin.getX()) > radius || Math.abs(n.getY() - origin.getY()) > radius
                                || Math.abs(n.getZ() - origin.getZ()) > radius) continue;
                        if (seen.add(n)) queue.add(n);
                    }
                }
            }
        }
        return out;
    }

    private static Direction[] planeDirections(Direction.Axis normal) {
        return switch (normal) {
            case X -> new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
            case Y -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            case Z -> new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        };
    }

    public static Component swatch(int color) {
        if (color == ShadeColors.NONE) return Component.translatable("fltint.color.none");
        return Component.literal("■ #" + ShadeColors.hex(color)).withStyle(s -> s.withColor(color));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("fltint.tooltip.color", swatch(color(stack))).withStyle(ChatFormatting.GRAY));
        Mode mode = mode(stack);
        Component modeName = Component.translatable("fltint.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
        tooltip.add((mode == Mode.BLOCK
                ? Component.translatable("fltint.tooltip.mode", modeName)
                : Component.translatable("fltint.tooltip.mode_radius", modeName, radius(stack))).withStyle(ChatFormatting.GRAY));
        if (mode != Mode.BLOCK && sameBlock(stack)) tooltip.add(Component.translatable("fltint.tooltip.same").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("fltint.tooltip.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
