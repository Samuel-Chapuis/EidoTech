package fr.Eidolyth;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.function.Supplier;
import fr.Eidolyth.block.VoxelBlock;
import fr.Eidolyth.block.plants.WaterPlant;
import fr.Eidolyth.block.plants.CattailBlock;
import fr.Eidolyth.block.plants.LeafLitterBlock;
import fr.Eidolyth.block.plants.OrangeLeafLitterBlock;
import fr.Eidolyth.block.plants.BiomColoredBlock;
import fr.Eidolyth.block.plants.CustomSaplingBlock;
import fr.Eidolyth.block.plants.MangroveCustomSapling;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, EidoPlants.MODID);

    public static final DeferredHolder<Block, Block> JUNGLE_GRASS = registerBlock("junglegrass", () -> new VoxelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)));
    public static final DeferredHolder<Block, Block> JUNGLE_GRASS_LIGHT = registerBlock("junglegrasslight", () -> new VoxelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)));

    public static final DeferredHolder<Block, Block> GRAPE_VINE = registerBlock("grapevine", () -> new BiomColoredBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().strength(0.2F).sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> GRAPY_GRAPE_VINE = registerBlock("grapygrapevine", () -> new BiomColoredBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().strength(0.2F).sound(SoundType.GRASS)));

    public static final DeferredHolder<Block, Block> BIG_LILY_PAD = registerBlock("biglilypad", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final DeferredHolder<Block, Block> BIG_LILY_PAD_PINK = registerBlock("biglilypad_pink", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final DeferredHolder<Block, Block> BIG_LILY_PAD_WHITE = registerBlock("biglilypad_white", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final DeferredHolder<Block, Block> BIG_LILY_PAD_RED = registerBlock("biglilypad_red", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final DeferredHolder<Block, Block> BIG_LILY_PAD_BLUE = registerBlock("biglilypad_blue", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));

    public static final DeferredHolder<Block, Block> ALGAE0 = registerBlock("algae0", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final DeferredHolder<Block, Block> ALGAE1 = registerBlock("algae1", () -> new WaterPlant(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.LILY_PAD)));

    public static final DeferredHolder<Block, Block> BIG_DEAD_BUSH = registerBlock("big_dead_bush", () -> new VoxelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).instabreak().sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> BIG_DEAD_TREE = registerBlock("big_dead_tree", () -> new VoxelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).instabreak().sound(SoundType.GRASS)));

    public static final DeferredHolder<Block, Block> CATTAILS1 = registerBlock("cattails1", () -> new CattailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> CATTAILS2 = registerBlock("cattails2", () -> new CattailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> SWAMP_CATTAILS1 = registerBlock("swamp_cattails1", () -> new CattailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> SWAMP_CATTAILS2 = registerBlock("swamp_cattails2", () -> new CattailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));

    public static final DeferredHolder<Block, Block> CACTUS_FLOWER = registerBlock("cactus_flower", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)));

    public static final DeferredHolder<Block, Block> LEAF_LITTER = registerBlock("leaf_litter", () -> new OrangeLeafLitterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS))); // Orange leaf litter
    public static final DeferredHolder<Block, Block> SPRING_LEAF_LITTER = registerBlock("spring_leaf_litter", () -> new LeafLitterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS))); // Default green

    public static final DeferredHolder<Block, Block> WILD_FLOWER = registerBlock("wildflower", () -> new LeafLitterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));
    public static final DeferredHolder<Block, Block> BLUET = registerBlock("bluet", () -> new LeafLitterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().instabreak().sound(SoundType.GRASS)));

    public static final DeferredHolder<Block, Block> HIBISCUS = registerBlock("hibiscus", () -> new FlowerBlock(
        MobEffects.HEAL,
        12,
        BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_ACCACIA_SAPLING = registerBlock("custom_acacia_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "acacia1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "acacia2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "acacia3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "acacia4")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_BIRCH_SAPLING = registerBlock("custom_birch_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "birch1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "birch2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "birch3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "birch4")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_BUSHY_BIRCH_SAPLING = registerBlock("custom_bushy_birch_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "bushy_birch1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "bushy_birch2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "bushy_birch3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "bushy_birch4"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "bushy_birch5")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_DARK_OAK_SAPLING = registerBlock("custom_dark_oak_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "dark_oak1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "dark_oak2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "dark_oak3")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_JUNGLE_SAPLING = registerBlock("custom_jungle_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "jungle1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "jungle2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "jungle3")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_MANGROVE_SAPLING = registerBlock("custom_mangrove_sapling", () -> new MangroveCustomSapling(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "mangrove1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "mangrove2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "mangrove3")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_OAK_SAPLING = registerBlock("custom_oak_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "oak1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "oak2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "oak3")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_PALM_SAPLING = registerBlock("custom_palm_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "palm1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "palm2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "palm3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "palm4"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "palm5")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_PLUME_SAPLING = registerBlock("custom_plume_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "plume1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "plume2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "plume3")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_SAKURA_SAPLING = registerBlock("custom_sakura_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sakura1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sakura2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sakura3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sakura4")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_SEQUOIA_SAPLING = registerBlock("custom_sequoia_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sequoia1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sequoia2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sequoia3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "sequoia4")
        )
    ));

    public static final DeferredHolder<Block, Block> CUSTOM_SPRUCE_SAPLING = registerBlock("custom_spruce_sapling", () -> new CustomSaplingBlock(
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS),
        List.of(
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce1"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce2"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce3"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce4"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce5"),
            ResourceLocation.fromNamespaceAndPath("eidoplants", "spruce6")
        )
    ));

    // Helper to register a block and corresponding item
    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, blockSupplier);
        // register BlockItem
        ModItems.registerBlockItem(name, toReturn);
        return toReturn;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
