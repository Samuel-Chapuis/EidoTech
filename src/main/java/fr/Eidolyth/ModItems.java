package fr.Eidolyth;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

// Minimal items registration using NeoForge DeferredRegister
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, EidoPlants.MODID);

    // Water-placeable block items - these are automatically registered by ModBlocks.registerBlock()
    // but we keep references here for easy access
    public static DeferredHolder<Item, Item> BIG_LILY_PAD_ITEM;
    public static DeferredHolder<Item, Item> BIG_LILY_PAD_PINK_ITEM;
    public static DeferredHolder<Item, Item> BIG_LILY_PAD_WHITE_ITEM;
    public static DeferredHolder<Item, Item> BIG_LILY_PAD_RED_ITEM;
    public static DeferredHolder<Item, Item> BIG_LILY_PAD_BLUE_ITEM;
    public static DeferredHolder<Item, Item> ALGAE0_ITEM;
    public static DeferredHolder<Item, Item> ALGAE1_ITEM;

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    // helper used by ModBlocks to register BlockItem for a DeferredHolder<Block, T>
    public static <T extends Block> DeferredHolder<Item, Item> registerBlockItem(String name, DeferredHolder<Block, T> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    // helper used by ModBlocks to register PlaceOnWaterBlockItem for water-placeable blocks
    public static <T extends Block> DeferredHolder<Item, Item> registerWaterBlockItem(String name, DeferredHolder<Block, T> block) {
        DeferredHolder<Item, Item> item = ITEMS.register(name, () -> new PlaceOnWaterBlockItem(block.get(), new Item.Properties()));
        
        // Assign references for water-placeable blocks
        if ("biglilypad".equals(name)) {
            BIG_LILY_PAD_ITEM = item;
        } else if ("biglilypad_pink".equals(name)) {
            BIG_LILY_PAD_PINK_ITEM = item;
        } else if ("biglilypad_white".equals(name)) {
            BIG_LILY_PAD_WHITE_ITEM = item;
        } else if ("biglilypad_red".equals(name)) {
            BIG_LILY_PAD_RED_ITEM = item;
        } else if ("biglilypad_blue".equals(name)) {
            BIG_LILY_PAD_BLUE_ITEM = item;
        } else if ("algae0".equals(name)) {
            ALGAE0_ITEM = item;
        } else if ("algae1".equals(name)) {
            ALGAE1_ITEM = item;
        }
        
        return item;
    }
}
