package faygolover.fltint;

import faygolover.fltint.command.ShadeCommand;
import faygolover.fltint.command.UndoHistory;
import faygolover.fltint.data.ProtectionEvents;
import faygolover.fltint.data.Shades;
import faygolover.fltint.item.BrushItem;
import faygolover.fltint.net.Net;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(FlTint.MODID)
public final class FlTint {
    public static final String MODID = "fltint";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<BrushItem> BRUSH = ITEMS.register("brush", () -> new BrushItem(new Item.Properties().stacksTo(1)));

    public FlTint() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(FlTint::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Net.register();

        MinecraftForge.EVENT_BUS.register(Shades.class);
        MinecraftForge.EVENT_BUS.register(ProtectionEvents.class);
        MinecraftForge.EVENT_BUS.addListener(FlTint::serverStarting);
        MinecraftForge.EVENT_BUS.register(UndoHistory.class);
        MinecraftForge.EVENT_BUS.addListener(ShadeCommand::register);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.fltint.client.ClientSetup.init(modBus));
    }

    /** WorldEdit грузится как обычный мод — подписываемся на его события, когда он точно готов. */
    private static void serverStarting(ServerAboutToStartEvent event) {
        if (ModList.get().isLoaded("worldedit")) faygolover.fltint.compat.WorldEditHook.register();
    }

    /** Вкладка «RP: разное» RP Core — от Core не зависим, ключ по имени. */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> RP_MISC =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    new net.minecraft.resources.ResourceLocation("rpcore", "misc"));

    /** Валик — во вкладке «RP: разное» RP Core; без Core — в «Операторских утилитах» (видна операторам с включённой настройкой). */
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (ModList.get().isLoaded("rpcore")) {
            if (event.getTabKey() == RP_MISC) event.accept(BRUSH);
        } else if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(BRUSH);
        }
    }
}
