package fr.thoridan.sl.item;

import fr.thoridan.sl.Techutilities;
import fr.thoridan.sl.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Techutilities.MODID);

    public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB = CREATIVE_MODE_TABS.register("techutilities",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.UNIVERSAL_SEAL_BLOCK.get()))
                    .title(Component.translatable("creative_tab.techutilities"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.UNIVERSAL_SEAL_BLOCK.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
