package faygolover.fltint.command;

import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/** Набор позиций для /shade: коробка по двум углам или выделение WorldEdit. Позиция в consumer — изменяемая, не хранить. */
public interface Area {
    long volume();

    void forEach(Consumer<BlockPos> action);

    static Area box(BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        return new Area() {
            @Override
            public long volume() {
                return (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            }

            @Override
            public void forEach(Consumer<BlockPos> action) {
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++)
                        for (int y = y0; y <= y1; y++) action.accept(p.set(x, y, z));
            }
        };
    }
}
