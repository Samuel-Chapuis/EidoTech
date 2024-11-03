package fr.thoridan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PrinterBlockEntity extends BlockEntity {
    private BlockPos storedTargetPos;
    private Rotation storedRotation;
    private String storedSchematicName;

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
    }

    public void placeStructureAt() {
        if (storedTargetPos == null || storedRotation == null || storedSchematicName == null) {
            System.out.println("No structure parameters stored.");
            return;
        }
        placeStructureAt(storedTargetPos, storedRotation, storedSchematicName);
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

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (storedTargetPos != null) {
            tag.putInt("TargetX", storedTargetPos.getX());
            tag.putInt("TargetY", storedTargetPos.getY());
            tag.putInt("TargetZ", storedTargetPos.getZ());
        }
        if (storedRotation != null) {
            tag.putString("Rotation", storedRotation.name());
        }
        if (storedSchematicName != null) {
            tag.putString("SchematicName", storedSchematicName);
        }

        // Console output
        System.out.println("Saving PrinterBlockEntity at " + getBlockPos());
        System.out.println("Stored Schematic Name: " + storedSchematicName);
        System.out.println("Stored Target Position: " + storedTargetPos);
        System.out.println("Stored Rotation: " + storedRotation);
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TargetX") && tag.contains("TargetY") && tag.contains("TargetZ")) {
            storedTargetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        } else {
            storedTargetPos = null;
        }
        if (tag.contains("Rotation")) {
            storedRotation = Rotation.valueOf(tag.getString("Rotation"));
        } else {
            storedRotation = null;
        }
        if (tag.contains("SchematicName")) {
            storedSchematicName = tag.getString("SchematicName");
        } else {
            storedSchematicName = null;
        }

        // Console output
        System.out.println("Loading PrinterBlockEntity at " + getBlockPos());
        System.out.println("Loaded Schematic Name: " + storedSchematicName);
        System.out.println("Loaded Target Position: " + storedTargetPos);
        System.out.println("Loaded Rotation: " + storedRotation);
    }


    public void setStructureParameters(BlockPos targetPos, Rotation rotation, String schematicName) {
        this.storedTargetPos = targetPos;
        this.storedRotation = rotation;
        this.storedSchematicName = schematicName;
        setChanged(); // Mark the block entity as changed to save data
        if (level != null && !level.isClientSide()) {
            // Notify the client about the change
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        // Console output
        System.out.println("Set Structure Parameters in PrinterBlockEntity at " + getBlockPos());
        System.out.println("Schematic Name: " + storedSchematicName);
        System.out.println("Target Position: " + storedTargetPos);
        System.out.println("Rotation: " + storedRotation);
    }

    public void setRotation(Rotation rotation) {
        this.storedRotation = rotation;
        setChanged(); // Mark the block entity as changed to save data
        if (level != null && !level.isClientSide()) {
            // Notify the client about the change
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        // Console output for debugging
        System.out.println("Set Rotation in PrinterBlockEntity at " + getBlockPos());
        System.out.println("Rotation: " + storedRotation);
    }

    public BlockPos getStoredTargetPos() {
        return storedTargetPos;
    }

    public Rotation getStoredRotation() {
        return storedRotation;
    }

    public String getStoredSchematicName() {
        return storedSchematicName;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        this.handleUpdateTag(pkt.getTag());
    }

    public void setSchematicName(String schematicName) {
        this.storedSchematicName = schematicName;
        setChanged(); // Mark the block entity as changed to save data
        if (level != null && !level.isClientSide()) {
            // Notify the client about the change
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        // Console output for debugging
        System.out.println("Set Schematic Name in PrinterBlockEntity at " + getBlockPos());
        System.out.println("Schematic Name: " + storedSchematicName);
    }

    public void setTargetPos(BlockPos targetPos) {
        this.storedTargetPos = targetPos;
        setChanged(); // Mark the block entity as changed to save data
        if (level != null && !level.isClientSide()) {
            // Notify the client about the change
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        // Console output for debugging
        System.out.println("Set Target Position in PrinterBlockEntity at " + getBlockPos());
        System.out.println("Target Position: " + storedTargetPos);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }


}
