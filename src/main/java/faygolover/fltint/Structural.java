package faygolover.fltint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Фильтр {@code solid}: «конструкция, а не мебель» — то, что ограничивает мир и движение.
 * Правило: у коллизии блока хотя бы одна грань закрыта целиком (полные блоки, ступени, плиты, книжные полки,
 * двери, люки). Не проходят: стеклянные панели, решётки, заборы, стены, мебель с тонкими ножками, цветы, факелы.
 * Поправки — теги fltint:structural (всегда да) и fltint:not_structural (всегда нет; там ковры и снег слоем).
 */
public final class Structural {
    public static final TagKey<Block> YES = TagKey.create(Registries.BLOCK, new ResourceLocation(FlTint.MODID, "structural"));
    public static final TagKey<Block> NO = TagKey.create(Registries.BLOCK, new ResourceLocation(FlTint.MODID, "not_structural"));

    private Structural() {}

    public static boolean test(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(NO)) return false;
        if (state.is(YES)) return true;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        for (Direction d : Direction.values()) {
            if (Block.isFaceFull(shape, d)) return true;
        }
        return false;
    }
}
