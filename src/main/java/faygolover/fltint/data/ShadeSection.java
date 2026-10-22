package faygolover.fltint.data;

import faygolover.fltint.ShadeColors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/**
 * Данные одной секции 16×16×16:
 * - оттенки: палитра до 255 цветов + индекс на блок (0 = без оттенка), 4 КБ;
 * - защита: бит на блок (512 байт), только если в секции есть защищённые блоки.
 * Пустые секции не хранятся. На клиенте экземпляры после публикации не меняются (копия при записи),
 * поэтому их можно читать из потоков сборки мешей.
 */
public final class ShadeSection {
    private static final int SIZE = 4096;

    private int[] palette;      // palette[0] не используется
    private int paletteSize;    // занятые слоты, включая 0
    private final byte[] index;
    private int count;          // покрашенных блоков
    private long[] guard;       // биты защиты или null
    private int guardCount;

    public ShadeSection() {
        this.palette = new int[8];
        this.paletteSize = 1;
        this.index = new byte[SIZE];
    }

    private ShadeSection(int[] palette, int paletteSize, byte[] index, int count, long[] guard, int guardCount) {
        this.palette = palette;
        this.paletteSize = paletteSize;
        this.index = index;
        this.count = count;
        this.guard = guard;
        this.guardCount = guardCount;
    }

    public static int localIndex(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    /** Цвет блока или {@link ShadeColors#NONE}. */
    public int get(int local) {
        int i = index[local] & 0xFF;
        return i == 0 ? ShadeColors.NONE : palette[i];
    }

    public boolean isProtected(int local) {
        return guard != null && (guard[local >> 6] & 1L << local) != 0;
    }

    public boolean isEmpty() {
        return count == 0 && guardCount == 0;
    }

    public boolean hasProtection() {
        return guardCount > 0;
    }

    /** Ставит цвет ({@link ShadeColors#NONE} = снять). false — в секции уже 255 разных цветов. */
    public boolean set(int local, int color) {
        color &= 0xFFFFFF;
        int old = index[local] & 0xFF;
        if (color == ShadeColors.NONE) {
            if (old != 0) {
                index[local] = 0;
                count--;
            }
            return true;
        }
        int slot = slotFor(color);
        if (slot < 0) {
            compact();
            slot = slotFor(color);
            if (slot < 0) return false;
        }
        if (old == 0) count++;
        index[local] = (byte) slot;
        return true;
    }

    public void setProtected(int local, boolean value) {
        if (isProtected(local) == value) return;
        if (value) {
            if (guard == null) guard = new long[SIZE / 64];
            guard[local >> 6] |= 1L << local;
            guardCount++;
        } else {
            guard[local >> 6] &= ~(1L << local);
            if (--guardCount == 0) guard = null;
        }
    }

    private int slotFor(int color) {
        for (int i = 1; i < paletteSize; i++) {
            if (palette[i] == color) return i;
        }
        if (paletteSize >= 256) return -1;
        if (paletteSize == palette.length) palette = Arrays.copyOf(palette, Math.min(256, palette.length * 2));
        palette[paletteSize] = color;
        return paletteSize++;
    }

    /** Выкидывает из палитры цвета, которых больше нет ни у одного блока. */
    private void compact() {
        boolean[] used = new boolean[paletteSize];
        for (byte b : index) used[b & 0xFF] = true;
        int[] remap = new int[paletteSize];
        int[] fresh = new int[palette.length];
        int size = 1;
        for (int i = 1; i < paletteSize; i++) {
            if (used[i]) {
                fresh[size] = palette[i];
                remap[i] = size++;
            }
        }
        for (int i = 0; i < SIZE; i++) {
            int b = index[i] & 0xFF;
            if (b != 0) index[i] = (byte) remap[b];
        }
        palette = fresh;
        paletteSize = size;
    }

    public ShadeSection copy() {
        return new ShadeSection(palette.clone(), paletteSize, index.clone(), count, guard == null ? null : guard.clone(), guardCount);
    }

    // ---- сохранение в чанк ----

    public CompoundTag save() {
        compact();
        CompoundTag tag = new CompoundTag();
        if (count > 0) {
            tag.putIntArray("p", Arrays.copyOf(palette, paletteSize));
            tag.putByteArray("d", index.clone());
        }
        if (guard != null) tag.putLongArray("g", guard.clone());
        return tag;
    }

    public static ShadeSection load(CompoundTag tag) {
        int[] p = tag.getIntArray("p");
        byte[] d = tag.getByteArray("d");
        long[] g = tag.getLongArray("g");
        if (p.length > 256 || d.length != 0 && d.length != SIZE) return null;
        return fromRaw(p, d.length == 0 ? null : d, g.length == SIZE / 64 ? g : null);
    }

    // ---- сеть ----

    public void write(FriendlyByteBuf buf) {
        compact();
        boolean shades = count > 0;
        buf.writeBoolean(shades);
        if (shades) {
            buf.writeVarIntArray(Arrays.copyOf(palette, paletteSize));
            buf.writeByteArray(index);
        }
        buf.writeBoolean(guard != null);
        if (guard != null) buf.writeLongArray(guard);
    }

    public static ShadeSection read(FriendlyByteBuf buf) {
        int[] p = new int[0];
        byte[] d = null;
        if (buf.readBoolean()) {
            p = buf.readVarIntArray(256);
            d = buf.readByteArray(SIZE);
        }
        long[] g = buf.readBoolean() ? buf.readLongArray(null, SIZE / 64) : null;
        if (d != null && d.length != SIZE || g != null && g.length != SIZE / 64) return null;
        return fromRaw(p, d, g);
    }

    private static ShadeSection fromRaw(int[] p, byte[] d, long[] g) {
        int[] palette = Arrays.copyOf(p.length == 0 ? new int[1] : p, Math.max(8, p.length));
        int paletteSize = Math.max(1, p.length);
        byte[] index = d == null ? new byte[SIZE] : d;
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            int b = index[i] & 0xFF;
            if (b >= paletteSize) index[i] = 0;
            else if (b != 0) count++;
        }
        int guardCount = 0;
        if (g != null) for (long l : g) guardCount += Long.bitCount(l);
        return new ShadeSection(palette, paletteSize, index, count, guardCount == 0 ? null : g, guardCount);
    }
}
