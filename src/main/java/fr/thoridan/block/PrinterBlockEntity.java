package fr.thoridan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PrinterBlockEntity extends BlockEntity {

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
    }

    public void placeStructureAt(BlockPos targetPos, Rotation rotation, String schematicName) {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            System.out.println("Level is not a ServerLevel");
            return;
        }

        // Get the schematics folder in the game directory
        File schematicsFolder = new File(FMLPaths.GAMEDIR.get().toFile(), "schematics");
        File schematicFile = new File(schematicsFolder, schematicName);

        if (!schematicFile.exists()) {
            System.out.println("Schematic file does not exist: " + schematicFile.getAbsolutePath());
            return;
        }

        CompoundTag nbtData;
        try {
            // Read the NBT data from the file
            nbtData = NbtIo.readCompressed(new FileInputStream(schematicFile));
        } catch (IOException e) {
            System.out.println("Failed to read schematic file: " + e.getMessage());
            return;
        }

        // Create a new StructureTemplate and load the NBT data
        StructureTemplate template = new StructureTemplate();

        // Obtain the HolderGetter<Block> from the ServerLevel's RegistryAccess
        HolderGetter<Block> holderGetter = serverLevel.registryAccess().lookupOrThrow(Registries.BLOCK);

        // Load the structure with the HolderGetter
        template.load(holderGetter, nbtData);

        // Create StructurePlaceSettings
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true);

        // Place the structure in the world
        boolean success = template.placeInWorld(serverLevel, targetPos, targetPos, settings, serverLevel.random, 2);

        if (!success) {
            System.out.println("Failed to place structure in world");
        }
    }
}
