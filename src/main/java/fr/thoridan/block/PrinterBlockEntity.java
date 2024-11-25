package fr.thoridan.block;

import fr.thoridan.menu.CustomItemStackHandler;
import fr.thoridan.menu.PrinterMenu;
import fr.thoridan.network.ModNetworking;
import fr.thoridan.network.printer.MissingItemsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;


import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrinterBlockEntity extends BlockEntity {
    private BlockPos storedTargetPos;
    private Rotation storedRotation;
    private String storedSchematicName;
    

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
    }


    public void placeStructureAt(BlockPos targetPos, Rotation rotation, String schematicName, ServerPlayer player) {
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

        // Map to store required blocks and counts
        Map<Item, Integer> requiredItems = new HashMap<>();

        // Read the palette from NBT data
        ListTag paletteTag = nbtData.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag stateTag = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(holderGetter, stateTag);
            palette.add(state);
        }

        // Read the blocks from NBT data
        ListTag blocksTag = nbtData.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateId = blockTag.getInt("state");

            BlockState blockState = palette.get(stateId);
            // Apply rotation to the block state
            blockState = blockState.rotate(rotation);

            Item item = blockState.getBlock().asItem();
            if (item != Items.AIR) {
                requiredItems.put(item, requiredItems.getOrDefault(item, 0) + 1);
            }
        }

        // **Declare and calculate missingItems here**
        Map<Item, Integer> missingItems = getMissingItems(requiredItems);
        if (!missingItems.isEmpty()) {
            System.out.println("Not enough items to place the structure");

            // Send packet to client to display missing items
            sendMissingItemsToClient(missingItems, player);

            return;
        }

        // Consume items from the inventory
        consumeItems(requiredItems);

        // Place the structure
        boolean success = template.placeInWorld(serverLevel, targetPos, targetPos, settings, serverLevel.random, 2);

        if (!success) {
            System.out.println("Failed to place structure in world");
        }
    }


    private void sendMissingItemsToClient(Map<Item, Integer> missingItems, ServerPlayer player) {
        // Create a packet and send it to the player
        ModNetworking.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MissingItemsPacket(missingItems));
    }



    private boolean hasRequiredItems(Map<Item, Integer> requiredItems) {
        Map<Item, Integer> inventoryItems = new HashMap<>();

        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                inventoryItems.put(item, inventoryItems.getOrDefault(item, 0) + stack.getCount());
            }
        }

        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            Item item = entry.getKey();
            int requiredCount = entry.getValue();
            int availableCount = inventoryItems.getOrDefault(item, 0);

            if (availableCount < requiredCount) {
                return false;
            }
        }
        return true;
    }

    private void consumeItems(Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            Item item = entry.getKey();
            int remaining = entry.getValue();

            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (stack.getItem() == item) {
                    int count = Math.min(stack.getCount(), remaining);
                    stack.shrink(count);
                    remaining -= count;

                    if (stack.isEmpty()) {
                        itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }

                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }
    }


    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
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
        itemHandler.deserializeNBT(tag.getCompound("inventory"));
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

    private final CustomItemStackHandler itemHandler = new CustomItemStackHandler(84) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    // Then declare lazyItemHandler
    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);


    @Override
    public void setRemoved() {
        super.setRemoved();
        lazyItemHandler.invalidate();
    }


    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(capability, side);
    }


    private Map<Item, Integer> getMissingItems(Map<Item, Integer> requiredItems) {
        Map<Item, Integer> inventoryItems = new HashMap<>();

        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                inventoryItems.put(item, inventoryItems.getOrDefault(item, 0) + stack.getCount());
            }
        }

        Map<Item, Integer> missingItems = new HashMap<>();

        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            Item item = entry.getKey();
            int requiredCount = entry.getValue();
            int availableCount = inventoryItems.getOrDefault(item, 0);

            if (availableCount < requiredCount) {
                missingItems.put(item, requiredCount - availableCount);
            }
        }

        return missingItems;
    }


}
