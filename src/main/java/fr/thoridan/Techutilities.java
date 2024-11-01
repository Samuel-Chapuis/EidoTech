package fr.thoridan;

import com.mojang.logging.LogUtils;
import fr.thoridan.block.ModBlockEntities;
import fr.thoridan.block.ModBlocks;
import fr.thoridan.item.ModCreativeModTabs;
import fr.thoridan.item.ModItems;
import fr.thoridan.menu.ModMenus;
import fr.thoridan.network.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Techutilities.MODID)
public class Techutilities {

    public static final String MODID = "techutilities";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Techutilities() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);					// Register the items
        ModBlocks.register(modEventBus);				// Register the blocks
        ModBlockEntities.register(modEventBus);
        ModCreativeModTabs.register(modEventBus);		// Ne marche pas tant que le bloc n'est pas rendu

        ModMenus.register(modEventBus);
        ModNetworking.registerPackets();


        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModBlocks.UNIVERSAL_SEAL_BLOCK);
        }
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(Techutilities.MODID, path);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }
}
