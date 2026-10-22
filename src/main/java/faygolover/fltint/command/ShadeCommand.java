package faygolover.fltint.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import faygolover.fltint.Config;
import faygolover.fltint.ShadeColors;
import faygolover.fltint.Structural;
import faygolover.fltint.compat.SecurityCraftCompat;
import faygolover.fltint.compat.WeMode;
import faygolover.fltint.data.Shades;
import faygolover.fltint.item.BrushItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * //shade (и /shade) — оттенки:
 *   set &lt;цвет&gt; [solid|блок], clear [solid|блок] — по выделению WorldEdit;
 *   convert [цвет] — укреплённые блоки SecurityCraft → ванильные + тот же оттенок + защита, без обновления соседей;
 *   region &lt;от&gt; &lt;до&gt; set|clear|convert ... — то же по координатам;
 *   get — что у блока под прицелом; undo; colors; we [keep|replace] — что WorldEdit делает с оттенком/защитой.
 * //protect — защита от слома (кроме ГМ) и взрывов: set|clear [solid|блок], region ..., undo.
 */
public final class ShadeCommand {
    private static final SimpleCommandExceptionType BAD_COLOR = new SimpleCommandExceptionType(Component.translatable("fltint.cmd.bad_color"));
    private static final SimpleCommandExceptionType NO_WORLDEDIT = new SimpleCommandExceptionType(Component.translatable("fltint.cmd.no_worldedit"));
    private static final SimpleCommandExceptionType NO_SC = new SimpleCommandExceptionType(Component.translatable("fltint.cmd.no_securitycraft"));
    private static final SimpleCommandExceptionType NO_TARGET = new SimpleCommandExceptionType(Component.translatable("fltint.cmd.no_target"));

    /** convert: без обновления соседей, без дропа, клиентам — да. Прикреплённые блоки (факелы, таблички) остаются. */
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private ShadeCommand() {}

    private interface AreaSource {
        Area get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    /** Какие блоки области трогать. null = все не-воздух. */
    private interface Filter {
        boolean test(ServerLevel level, BlockPos pos, BlockState state);
    }

    private interface FilteredOp {
        int run(CommandContext<CommandSourceStack> ctx, @Nullable Filter filter) throws CommandSyntaxException;
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        CommandBuildContext build = event.getBuildContext();

        AreaSource selection = ShadeCommand::selection;
        AreaSource region = ctx -> Area.box(BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"));

        LiteralCommandNode<CommandSourceStack> shade = d.register(Commands.literal("/shade")
                .requires(s -> s.hasPermission(2))
                .then(shadeSet(selection, build))
                .then(shadeClear(selection, build))
                .then(convertOp(selection))
                .then(Commands.literal("region")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(shadeSet(region, build))
                                        .then(shadeClear(region, build))
                                        .then(convertOp(region)))))
                .then(Commands.literal("get").executes(ShadeCommand::get))
                .then(Commands.literal("undo").executes(ShadeCommand::undo))
                .then(Commands.literal("colors").executes(ShadeCommand::colors))
                .then(Commands.literal("we")
                        .executes(ctx -> weMode(ctx, null))
                        .then(Commands.literal("keep").executes(ctx -> weMode(ctx, true)))
                        .then(Commands.literal("replace").executes(ctx -> weMode(ctx, false)))));
        d.register(Commands.literal("shade").requires(s -> s.hasPermission(2)).redirect(shade));

        d.register(Commands.literal("/protect")
                .requires(s -> s.hasPermission(2))
                .then(protectOp("set", true, selection, build))
                .then(protectOp("clear", false, selection, build))
                .then(Commands.literal("region")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(protectOp("set", true, region, build))
                                        .then(protectOp("clear", false, region, build)))))
                .then(Commands.literal("undo").executes(ShadeCommand::undo)));
    }

    // ---- дерево ----

    /** [solid | блок] в конце команды. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withFilter(T node, CommandBuildContext build, FilteredOp op) {
        return node
                .executes(ctx -> op.run(ctx, null))
                .then(Commands.literal("solid").executes(ctx -> op.run(ctx, (level, pos, state) -> Structural.test(level, pos, state))))
                .then(Commands.argument("block", BlockPredicateArgument.blockPredicate(build))
                        .executes(ctx -> {
                            Predicate<BlockInWorld> p = BlockPredicateArgument.getBlockPredicate(ctx, "block");
                            return op.run(ctx, (level, pos, state) -> p.test(new BlockInWorld(level, pos, true)));
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shadeSet(AreaSource area, CommandBuildContext build) {
        return Commands.literal("set").then(withFilter(Commands.argument("color", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ShadeColors.PRESETS.keySet(), b)), build,
                (ctx, filter) -> paint(ctx, area.get(ctx), color(ctx, "color"), filter)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shadeClear(AreaSource area, CommandBuildContext build) {
        return withFilter(Commands.literal("clear"), build, (ctx, filter) -> paint(ctx, area.get(ctx), ShadeColors.NONE, filter));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> protectOp(String name, boolean value, AreaSource area, CommandBuildContext build) {
        return withFilter(Commands.literal(name), build, (ctx, filter) -> protect(ctx, area.get(ctx), value, filter));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> convertOp(AreaSource area) {
        return Commands.literal("convert")
                .executes(ctx -> convert(ctx, area.get(ctx), ShadeColors.REINFORCED))
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ShadeColors.PRESETS.keySet(), b))
                        .executes(ctx -> convert(ctx, area.get(ctx), color(ctx, "color"))));
    }

    // ---- общее ----

    private static int color(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        int c = ShadeColors.parse(StringArgumentType.getString(ctx, arg));
        if (c < 0) throw BAD_COLOR.create();
        return c;
    }

    private static Area selection(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModList.get().isLoaded("worldedit")) throw NO_WORLDEDIT.create();
        try {
            return WorldEditSelection.get(ctx.getSource().getPlayerOrException());
        } catch (WorldEditSelection.SelectionException e) {
            throw new SimpleCommandExceptionType(e.message).create();
        }
    }

    private static void checkVolume(Area area) throws CommandSyntaxException {
        if (area.volume() > Config.MAX_VOLUME.get()) {
            throw new SimpleCommandExceptionType(Component.translatable("fltint.cmd.too_big", area.volume(), Config.MAX_VOLUME.get())).create();
        }
    }

    private static boolean accept(ServerLevel level, BlockPos pos, BlockState state, @Nullable Filter filter) {
        return !state.isAir() && (filter == null || filter.test(level, pos, state));
    }

    // ---- операции ----

    private static int paint(CommandContext<CommandSourceStack> ctx, Area area, int color, @Nullable Filter filter)
            throws CommandSyntaxException {
        checkVolume(area);
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        UndoHistory.Recorder undo = UndoHistory.begin(src.getPlayer(), level);
        int[] counts = new int[2]; // изменено, не влезло в палитру
        area.forEach(pos -> {
            if (!accept(level, pos, level.getBlockState(pos), filter)) return;
            int old = Shades.set(level, pos, color);
            if (old == Shades.FAILED) counts[1]++;
            else if (old != color) {
                counts[0]++;
                undo.add(pos, old);
            }
        });
        undo.commit();
        Component what = color == ShadeColors.NONE
                ? Component.translatable("fltint.cmd.cleared", counts[0])
                : Component.translatable("fltint.cmd.painted", counts[0], BrushItem.swatch(color));
        src.sendSuccess(() -> what, true);
        if (counts[1] > 0) src.sendFailure(Component.translatable("fltint.cmd.palette_full", counts[1]));
        if (undo.overflowed()) src.sendSystemMessage(Component.translatable("fltint.cmd.no_undo"));
        return counts[0];
    }

    private static int protect(CommandContext<CommandSourceStack> ctx, Area area, boolean value, @Nullable Filter filter)
            throws CommandSyntaxException {
        checkVolume(area);
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        UndoHistory.Recorder undo = UndoHistory.beginProtect(src.getPlayer(), level);
        int[] changed = new int[1];
        area.forEach(pos -> {
            if (!accept(level, pos, level.getBlockState(pos), filter)) return;
            boolean old = Shades.setProtected(level, pos, value);
            if (old != value) {
                changed[0]++;
                undo.add(pos, old ? 1 : 0);
            }
        });
        undo.commit();
        src.sendSuccess(() -> Component.translatable(value ? "fltint.cmd.protected" : "fltint.cmd.unprotected", changed[0]), true);
        if (undo.overflowed()) src.sendSystemMessage(Component.translatable("fltint.cmd.no_undo"));
        return changed[0];
    }

    private static int convert(CommandContext<CommandSourceStack> ctx, Area area, int reinforcedTint) throws CommandSyntaxException {
        if (!SecurityCraftCompat.available()) throw NO_SC.create();
        checkVolume(area);
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        int[] counts = new int[2]; // заменено, пропущено (у ванильного блока есть блок-энтити: сундук, воронка...)
        Shades.runBypassing(() -> area.forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!SecurityCraftCompat.isReinforced(state.getBlock())) return;
            BlockState vanilla = SecurityCraftCompat.toVanilla(level, pos, state);
            if (vanilla == null || vanilla.isAir() || vanilla.hasBlockEntity()) {
                counts[1]++;
                return;
            }
            int shade = SecurityCraftCompat.shadeFor(state.getBlock(), reinforcedTint);
            if (!level.setBlock(pos, vanilla, QUIET_FLAGS)) return;
            Shades.set(level, pos, shade);
            Shades.setProtected(level, pos, true);
            counts[0]++;
        }));
        src.sendSuccess(() -> Component.translatable("fltint.cmd.converted", counts[0]), true);
        if (counts[1] > 0) src.sendSystemMessage(Component.translatable("fltint.cmd.convert_skipped", counts[1]));
        return counts[0];
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HitResult hit = player.pick(20.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) throw NO_TARGET.create();
        BlockPos pos = bhr.getBlockPos();
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        int color = Shades.get(level, pos);
        MutableComponent shade;
        if (color == ShadeColors.NONE) {
            shade = Component.translatable("fltint.color.none").withStyle(ChatFormatting.GRAY);
        } else {
            String hex = ShadeColors.hex(color);
            shade = BrushItem.swatch(color).copy().withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, hex))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("fltint.cmd.get.copy"))));
        }
        Component prot = Component.translatable(Shades.isProtected(level, pos) ? "fltint.cmd.get.protected" : "fltint.cmd.get.unprotected");
        ctx.getSource().sendSuccess(() -> Component.translatable("fltint.cmd.get",
                state.getBlock().getName(), pos.getX() + " " + pos.getY() + " " + pos.getZ(), shade, prot), false);
        return color == ShadeColors.NONE ? 0 : 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int n = UndoHistory.undo(ctx.getSource().getPlayerOrException());
        if (n < 0) {
            ctx.getSource().sendFailure(Component.translatable("fltint.cmd.nothing_to_undo"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("fltint.cmd.undone", n), true);
        return n;
    }

    private static int colors(CommandContext<CommandSourceStack> ctx) {
        MutableComponent line = Component.translatable("fltint.cmd.colors").copy();
        ShadeColors.PRESETS.forEach((name, c) -> line.append(" ").append(Component.literal(name).withStyle(s -> s.withColor(c))));
        ctx.getSource().sendSuccess(() -> line, false);
        ctx.getSource().sendSuccess(() -> Component.translatable("fltint.cmd.colors_help"), false);
        return ShadeColors.PRESETS.size();
    }

    private static int weMode(CommandContext<CommandSourceStack> ctx, @Nullable Boolean keep) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (keep != null) WeMode.setKeep(player, keep);
        boolean now = WeMode.keep(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(now ? "fltint.cmd.we.keep" : "fltint.cmd.we.replace"), false);
        return now ? 1 : 0;
    }
}
