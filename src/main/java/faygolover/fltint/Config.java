package faygolover.fltint;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_VOLUME;
    public static final ForgeConfigSpec.IntValue MAX_UNDO_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_VOLUME = b.comment("Max blocks in one /shade set|clear|convert area (WorldEdit selection or region). Bigger areas are refused.")
                .defineInRange("max_volume", 2_000_000, 1, 64_000_000);
        MAX_UNDO_BLOCKS = b.comment("Operations that changed more blocks than this are not kept for /shade undo (memory).")
                .defineInRange("max_undo_blocks", 500_000, 0, 16_000_000);
        SPEC = b.build();
    }

    private Config() {}
}
