package fr.thoridan.client;

import fr.thoridan.Techutilities;
import fr.thoridan.block.PrinterBlockEntity;
import fr.thoridan.client.printer.render.PrinterBlockEntityRenderer;
import fr.thoridan.client.printer.ui.PrinterScreen;
import fr.thoridan.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Techutilities.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.PRINTER_MENU.get(), PrinterScreen::new);
        });
    }
}

