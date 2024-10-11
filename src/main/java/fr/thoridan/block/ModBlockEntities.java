package fr.thoridan.block;

import fr.thoridan.Techutilities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Techutilities.MODID);

    public static final RegistryObject<BlockEntityType<UniversalSealBlockEntity>> UNIVERSAL_SEAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("universal_seal_block_entity",
                    () -> BlockEntityType.Builder.of(UniversalSealBlockEntity::new, ModBlocks.UNIVERSAL_SEAL_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
