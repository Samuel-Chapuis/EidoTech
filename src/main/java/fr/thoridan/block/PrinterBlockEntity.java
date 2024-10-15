package fr.thoridan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PrinterBlockEntity extends BlockEntity {
    private String selectedSchematic = "";

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
//        System.out.println("PrinterBlockEntity created at " + pos + " on " + (this.level != null && this.level.isClientSide ? "client" : "server") + " side");
    }


    public String getSelectedSchematic() {
        return selectedSchematic;
    }

    public void setSelectedSchematic(String schematic) {
        this.selectedSchematic = schematic;
        setChanged(); // Mark the block entity as changed to save the data
        // Notify clients of the change
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        selectedSchematic = tag.getString("SelectedSchematic");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("SelectedSchematic", selectedSchematic);
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
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void placeStructure() {
        if (level != null && !level.isClientSide && !selectedSchematic.isEmpty()) {
            ServerLevel serverLevel = (ServerLevel) level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();

            File schematicsFolder = serverLevel.getServer().getServerDirectory().toPath().resolve("schematics").toFile();
            File schematicFile = new File(schematicsFolder, selectedSchematic);

            try {
                StructureTemplate structure = loadStructureFromFile(schematicFile, serverLevel);
                if (structure != null) {
                    BlockPos pos = worldPosition.above(); // Adjust as needed
                    StructurePlaceSettings settings = new StructurePlaceSettings()
                            .setRotation(Rotation.NONE)
                            .setMirror(Mirror.NONE)
                            .setRandom(serverLevel.random)
                            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

                    // Place the structure
                    structure.placeInWorld(serverLevel, pos, pos, settings, serverLevel.random, 2);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private StructureTemplate loadStructureFromFile(File file, ServerLevel serverLevel) throws IOException {
        CompoundTag nbt;
        try (FileInputStream fis = new FileInputStream(file)) {
            nbt = NbtIo.readCompressed(fis);
        }

        return serverLevel.getStructureManager().readStructure(nbt);
    }

    public void placeStructureAt(BlockPos targetPos, Rotation rotation) {
        if (level != null && !level.isClientSide && !selectedSchematic.isEmpty()) {
            ServerLevel serverLevel = (ServerLevel) level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();

            File schematicsFolder = serverLevel.getServer().getServerDirectory().toPath().resolve("schematics").toFile();
            File schematicFile = new File(schematicsFolder, selectedSchematic);

            try {
                StructureTemplate structure = loadStructureFromFile(schematicFile, serverLevel);
                if (structure != null) {
                    StructurePlaceSettings settings = new StructurePlaceSettings()
                            .setRotation(rotation)
                            .setMirror(Mirror.NONE)
                            .setRandom(serverLevel.random)
                            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

                    // Place the structure at the target position
                    structure.placeInWorld(serverLevel, targetPos, targetPos, settings, serverLevel.random, 2);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

