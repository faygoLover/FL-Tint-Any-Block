package faygolover.fltint.data;

import faygolover.fltint.ShadeColors;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Оттенки одного чанка на сервере: секция по Y → {@link ShadeSection}. Живёт в поле LevelChunk (см. LevelChunkMixin). */
public final class ShadeData {
    public static final String NBT_KEY = "fltint";

    private final Int2ObjectMap<ShadeSection> sections = new Int2ObjectOpenHashMap<>();

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public Int2ObjectMap<ShadeSection> sections() {
        return sections;
    }

    public ShadeSection section(int sectionY) {
        return sections.get(sectionY);
    }

    public int get(int x, int y, int z) {
        ShadeSection s = sections.get(y >> 4);
        return s == null ? ShadeColors.NONE : s.get(ShadeSection.localIndex(x, y, z));
    }

    /** false — в секции кончилась палитра (255 цветов). */
    public boolean set(int x, int y, int z, int color) {
        int sy = y >> 4;
        ShadeSection s = sections.get(sy);
        if (s == null) {
            if ((color & 0xFFFFFF) == ShadeColors.NONE) return true;
            s = new ShadeSection();
            sections.put(sy, s);
        }
        boolean ok = s.set(ShadeSection.localIndex(x, y, z), color);
        if (s.isEmpty()) sections.remove(sy);
        return ok;
    }

    public boolean isProtected(int x, int y, int z) {
        ShadeSection s = sections.get(y >> 4);
        return s != null && s.isProtected(ShadeSection.localIndex(x, y, z));
    }

    public void setProtected(int x, int y, int z, boolean value) {
        int sy = y >> 4;
        ShadeSection s = sections.get(sy);
        if (s == null) {
            if (!value) return;
            s = new ShadeSection();
            sections.put(sy, s);
        }
        s.setProtected(ShadeSection.localIndex(x, y, z), value);
        if (s.isEmpty()) sections.remove(sy);
    }

    /** Блок исчез: снять и оттенок, и защиту. true — что-то было. */
    public boolean clear(int x, int y, int z) {
        ShadeSection s = sections.get(y >> 4);
        if (s == null) return false;
        int local = ShadeSection.localIndex(x, y, z);
        if (s.get(local) == ShadeColors.NONE && !s.isProtected(local)) return false;
        s.set(local, ShadeColors.NONE);
        s.setProtected(local, false);
        if (s.isEmpty()) sections.remove(y >> 4);
        return true;
    }

    public CompoundTag save() {
        ListTag list = new ListTag();
        for (Int2ObjectMap.Entry<ShadeSection> e : sections.int2ObjectEntrySet()) {
            CompoundTag t = e.getValue().save();
            t.putInt("y", e.getIntKey());
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("s", list);
        return tag;
    }

    public static ShadeData load(CompoundTag tag) {
        ShadeData data = new ShadeData();
        ListTag list = tag.getList("s", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ShadeSection s = ShadeSection.load(t);
            if (s != null && !s.isEmpty()) data.sections.put(t.getInt("y"), s);
        }
        return data;
    }
}
