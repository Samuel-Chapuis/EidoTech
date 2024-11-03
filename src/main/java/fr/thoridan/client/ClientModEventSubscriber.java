package fr.thoridan.client;

import fr.thoridan.Techutilities;
import fr.thoridan.block.ModBlockEntities;
import fr.thoridan.client.printer.render.PrinterBlockEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Techutilities.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModEventSubscriber {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), PrinterBlockEntityRenderer::new);
    }
}
