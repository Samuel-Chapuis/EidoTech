package fr.thoridan.sl.item;

import fr.thoridan.sl.Techutilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Techutilities.MODID);
    public static void register(IEventBus eventBus) { // This is the register method
        ITEMS.register(eventBus);
    }
}
