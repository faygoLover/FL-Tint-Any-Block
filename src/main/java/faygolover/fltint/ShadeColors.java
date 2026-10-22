package faygolover.fltint;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Оттенок = множитель цвета 0xRRGGBB: каждый канал текстуры умножается на канал/255.
 * 0xFFFFFF = без оттенка (такие не храним). Светлее текстуры сделать нельзя — только темнее и/или цветнее.
 */
public final class ShadeColors {
    public static final int NONE = 0xFFFFFF;
    /** Как у укреплённых блоков SecurityCraft по умолчанию (reinforced_block_tint_color). */
    public static final int REINFORCED = 0x999999;

    /** Готовые оттенки: имя для команды → цвет. Порядок = порядок в окне валика. */
    public static final Map<String, Integer> PRESETS = new LinkedHashMap<>();

    static {
        for (int p = 90; p >= 20; p -= 10) PRESETS.put("g" + p, gray(p));
        PRESETS.put("reinforced", REINFORCED);
        PRESETS.put("soot", 0x6E6A66);
        PRESETS.put("rust", 0xC8906A);
        PRESETS.put("moss", 0x9AB27E);
        PRESETS.put("toxic", 0xB4CC6A);
        PRESETS.put("sand", 0xE6D2A8);
        PRESETS.put("warm", 0xF0D8B8);
        PRESETS.put("cold", 0xB4C4DC);
        PRESETS.put("steel", 0x8C98A8);
        PRESETS.put("blood", 0xB46464);
        PRESETS.put("brick", 0xC87C64);
        PRESETS.put("olive", 0xA0A070);
        PRESETS.put("navy", 0x7080A8);
        PRESETS.put("violet", 0xA88CC0);
        PRESETS.put("pink", 0xE6B4C0);
    }

    private ShadeColors() {}

    /** Серый на {@code percent}% яркости текстуры. */
    public static int gray(int percent) {
        int v = Math.round(Math.max(0, Math.min(100, percent)) * 255 / 100f);
        return v << 16 | v << 8 | v;
    }

    /**
     * Разбор цвета из команды: имя из {@link #PRESETS}, 6 hex-цифр ({@code 999999}, {@code 0x999999})
     * или яркость в процентах ({@code 60} = серый 60%). Возвращает -1, если не разобрать.
     */
    public static int parse(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        Integer preset = PRESETS.get(t);
        if (preset != null) return preset;
        if (t.equals("none") || t.equals("clear")) return NONE;
        if (t.startsWith("#")) t = t.substring(1);
        if (t.startsWith("0x")) t = t.substring(2);
        if (t.length() == 6) {
            try {
                return Integer.parseInt(t, 16);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        if (t.length() <= 3) {
            try {
                int p = Integer.parseInt(t);
                if (p >= 0 && p <= 100) return gray(p);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    /** Покомпонентное умножение двух RGB (как ARGB32.multiply у SecurityCraft). */
    public static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    /** Умножение ARGB вершины на оттенок, альфа не трогается. */
    public static int applyToArgb(int argb, int shade) {
        return (argb & 0xFF000000) | multiply(argb & 0xFFFFFF, shade);
    }

    /** То же для ABGR (так цвета вершин хранит Embeddium): красный в младшем байте. */
    public static int applyToAbgr(int abgr, int shade) {
        int swapped = (shade & 0xFF) << 16 | (shade & 0xFF00) | (shade >> 16) & 0xFF;
        return applyToArgb(abgr, swapped);
    }
}
