package fr.thoridan.item;

import fr.thoridan.Techutilities;
import fr.thoridan.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Techutilities.MODID);

    public static void register(IEventBus eventBus) { // This is the register method
        ITEMS.register(eventBus);
    }
}
