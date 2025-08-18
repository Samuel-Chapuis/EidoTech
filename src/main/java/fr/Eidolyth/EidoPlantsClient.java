package fr.Eidolyth;

import fr.Eidolyth.block.plants.LeafLitterBlock;
import fr.Eidolyth.block.plants.OrangeLeafLitterBlock;
import fr.Eidolyth.block.plants.CutoutFlowerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EidoPlants.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = EidoPlants.MODID, value = Dist.CLIENT)
public class EidoPlantsClient {
    public EidoPlantsClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        EidoPlants.LOGGER.info("HELLO FROM CLIENT SETUP");
        EidoPlants.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        // Register color handlers for BiomColoredBlock instances
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null) {
                return BiomeColors.getAverageFoliageColor(level, pos);
            }
            return GrassColor.getDefaultColor();
        }, 
        ModBlocks.GRAPE_VINE.get(),
        ModBlocks.GRAPY_GRAPE_VINE.get()
        );
        
        // Register color handler for normal LEAF_LITTER - use block's own color method
        event.register((state, level, pos, tintIndex) -> {
            Block block = state.getBlock();
            if (block instanceof OrangeLeafLitterBlock orangeLeafLitterBlock) {
                return orangeLeafLitterBlock.getColor(state, level, pos, tintIndex);
            }
            return GrassColor.getDefaultColor();
        }, ModBlocks.LEAF_LITTER.get());
        
        // Register color handler for SPRING_LEAF_LITTER - use block's own color method
        event.register((state, level, pos, tintIndex) -> {
            Block block = state.getBlock();
            if (block instanceof LeafLitterBlock leafLitterBlock) {
                return leafLitterBlock.getColor(state, level, pos, tintIndex);
            }
            return GrassColor.getDefaultColor();
        }, ModBlocks.SPRING_LEAF_LITTER.get());
        
        // Register color handlers for CutoutFlowerBlock instances (wildflower, bluet)
        event.register((state, level, pos, tintIndex) -> {
            Block block = state.getBlock();
            if (block instanceof CutoutFlowerBlock cutoutFlowerBlock) {
                return cutoutFlowerBlock.getColor(state, level, pos, tintIndex);
            }
            return GrassColor.getDefaultColor();
        }, 
        ModBlocks.WILD_FLOWER.get(),
        ModBlocks.BLUET.get()
        );
    }
}
