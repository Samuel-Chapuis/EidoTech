package fr.thoridan.block;

import com.mojang.authlib.GameProfile;
import fr.thoridan.menu.CustomItemStackHandler;
import fr.thoridan.network.ModNetworking;
import fr.thoridan.network.printer.MissingItemsPacket;
import fr.thoridan.network.printer.PlacementDelayUpdatePacket;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.*;


import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PrinterBlockEntity extends BlockEntity {
    private BlockPos pendingTargetPos;
    private Rotation pendingRotation;
    private String pendingSchematicName;
    private BlockPos storedTargetPos;
    private Rotation storedRotation;
    private String storedSchematicName;
    private int placementDelayTicks = -1;
    private int clientPlacementDelayTicks = -1;
    private UUID ownerUUID;

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        System.out.println("Player UUID set to " + ownerUUID);
        setChanged();
    }



    public void placeStructureAt(BlockPos targetPos, Rotation rotation, String schematicName, ServerPlayer player) {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (placementDelayTicks > 0) {
            // Inform the player that a placement is already in progress
            player.displayClientMessage(Component.literal("A structure placement is already in progress."), true);
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

        // Load NBT data to calculate required items
        CompoundTag nbtData;
        try {
            nbtData = NbtIo.readCompressed(new FileInputStream(schematicFile));
        } catch (IOException e) {
            System.out.println("Failed to read schematic file: " + e.getMessage());
            return;
        }

        // Obtain the HolderGetter<Block> from the ServerLevel's RegistryAccess
        HolderGetter<Block> holderGetter = serverLevel.registryAccess().lookupOrThrow(Registries.BLOCK);

        // Read the palette from NBT data
        ListTag paletteTag = nbtData.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag stateTag = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(holderGetter, stateTag);
            palette.add(state);
        }

        // Read the blocks from NBT data and calculate required items
        ListTag blocksTag = nbtData.getList("blocks", Tag.TAG_COMPOUND);
        Map<Item, Integer> requiredItems = new HashMap<>();

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

        // Check for missing items
        Map<Item, Integer> missingItems = getMissingItems(requiredItems);
        if (!missingItems.isEmpty()) {
            System.out.println("Not enough items to place the structure");

            // Send packet to client to display missing items
            sendMissingItemsToClient(missingItems, player);

            return;
        }

        // Consume items from the inventory
        consumeItems(requiredItems);

        // Initiate the placement delay
        this.pendingTargetPos = targetPos;
        this.pendingRotation = rotation;
        this.pendingSchematicName = schematicName;
        this.placementDelayTicks = blocksTag.size()*1; // 1 tick per block
        this.setChanged(); // Mark the block entity as changed
    }


    private void performStructurePlacement() {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            System.out.println("Level is not a ServerLevel");
            return;
        }

        System.out.println("Starting performStructurePlacement");

        // Load the schematic file
        File schematicsFolder = new File(FMLPaths.GAMEDIR.get().toFile(), "schematics");
        File schematicFile = new File(schematicsFolder, this.pendingSchematicName);

        if (!schematicFile.exists()) {
            System.out.println("Schematic file does not exist: " + schematicFile.getAbsolutePath());
            return;
        }

        // Create the FakePlayer
        GameProfile ownerProfile = new GameProfile(ownerUUID, "[Printer Owner]");
        FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, ownerProfile);
        fakePlayer.setGameMode(GameType.SURVIVAL);
        System.out.println("FakePlayer UUID: " + fakePlayer.getUUID());

        CompoundTag nbtData;
        try {
            nbtData = NbtIo.readCompressed(new FileInputStream(schematicFile));
            System.out.println("Schematic NBT data loaded successfully.");
        } catch (IOException e) {
            System.out.println("Failed to read schematic file: " + e.getMessage());
            return;
        }

        // Obtain the HolderGetter<Block> from the ServerLevel's RegistryAccess
        HolderGetter<Block> holderGetter = serverLevel.registryAccess().lookupOrThrow(Registries.BLOCK);

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
        System.out.println("Total blocks to place: " + blocksTag.size());

        // Iterate over each block and simulate placement
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);

            // Get the position
            ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
            int x = posList.getInt(0);
            int y = posList.getInt(1);
            int z = posList.getInt(2);
            BlockPos blockPos = new BlockPos(x, y, z);

            // Get the state index and retrieve the BlockState
            int stateId = blockTag.getInt("state");
            BlockState blockState = palette.get(stateId);

            // Apply rotation and mirror transformations
            blockState = blockState.mirror(Mirror.NONE).rotate(this.pendingRotation);
            blockPos = transformBlockPos(blockPos, this.pendingRotation);

            // Offset the block position by the target position
            BlockPos worldPos = blockPos.offset(this.pendingTargetPos);

            // Get NBT data if present
            CompoundTag nbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt") : null;

            System.out.println("Attempting to place block: " + blockState + " at position: " + worldPos);

            simulateBlockPlacement(fakePlayer, serverLevel, blockState, worldPos, nbt);
        }

        // Reset pending variables
        this.pendingTargetPos = null;
        this.pendingRotation = null;
        this.pendingSchematicName = null;
        this.placementDelayTicks = -1;
        this.setChanged();
        System.out.println("Finished performStructurePlacement");
    }



    private BlockPos transformBlockPos(BlockPos pos, Rotation rotation) {
        // Transform the block position according to the rotation
        // The center of rotation is the origin (0,0,0) of the structure
        switch (rotation) {
            case NONE:
                return pos;
            case CLOCKWISE_90:
                return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180:
                return new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default:
                return pos;
        }
    }




    private void simulateBlockPlacement(FakePlayer fakePlayer, ServerLevel level, BlockState blockState, BlockPos pos, @Nullable CompoundTag nbt) {
        System.out.println("Simulating block placement at position: " + pos);

        // Prepare the item stack for the block
        ItemStack stack = new ItemStack(blockState.getBlock().asItem());
        if (stack.isEmpty()) {
            System.out.println("ItemStack is empty for block: " + blockState.getBlock());
            return;
        }

        stack.setCount(1);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        System.out.println("ItemStack in FakePlayer's hand: " + stack);

        // Set the FakePlayer's position near the block to be placed
        fakePlayer.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);

        // Create a BlockHitResult for block placement
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        // Simulate the block placement
        InteractionResult result = fakePlayer.gameMode.useItemOn(fakePlayer, level, stack, InteractionHand.MAIN_HAND, hitResult);
        System.out.println("InteractionResult: " + result);

        if (!result.consumesAction()) {
            // If placement failed, output debug message
            System.out.println("Block placement was denied or failed for block at position: " + pos);
        } else {
            System.out.println("Block placed successfully at position: " + pos);
        }

        // Handle block entity data if present
        if (nbt != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.load(nbt);
                System.out.println("BlockEntity data loaded for block at position: " + pos);
            }
        }
    }





    public static void tick(Level level, BlockPos pos, BlockState state, PrinterBlockEntity blockEntity) {
        if (blockEntity.placementDelayTicks > 0) {
            blockEntity.placementDelayTicks--;
            if (blockEntity.placementDelayTicks == 0) {
                blockEntity.performStructurePlacement();
                blockEntity.setChanged(); // Mark the block entity as changed
            }
            // Send update to client
            if (level instanceof ServerLevel serverLevel) {
                ModNetworking.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(pos)),
                        new PlacementDelayUpdatePacket(pos, blockEntity.placementDelayTicks));
            }
        }
    }

    private void sendMissingItemsToClient(Map<Item, Integer> missingItems, ServerPlayer player) {
        // Create a packet and send it to the player
        ModNetworking.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MissingItemsPacket(missingItems));
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

        // Save pending variables
        if (pendingTargetPos != null) {
            tag.putInt("PendingTargetX", pendingTargetPos.getX());
            tag.putInt("PendingTargetY", pendingTargetPos.getY());
            tag.putInt("PendingTargetZ", pendingTargetPos.getZ());
        }
        if (pendingRotation != null) {
            tag.putString("PendingRotation", pendingRotation.name());
        }
        if (pendingSchematicName != null) {
            tag.putString("PendingSchematicName", pendingSchematicName);
        }
        tag.putInt("PlacementDelayTicks", placementDelayTicks);

        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }

        // Console output
//        System.out.println("Saving PrinterBlockEntity at " + getBlockPos());
//        System.out.println("Stored Schematic Name: " + storedSchematicName);
//        System.out.println("Stored Target Position: " + storedTargetPos);
//        System.out.println("Stored Rotation: " + storedRotation);
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

        // Load pending variables
        if (tag.contains("PendingTargetX") && tag.contains("PendingTargetY") && tag.contains("PendingTargetZ")) {
            pendingTargetPos = new BlockPos(tag.getInt("PendingTargetX"), tag.getInt("PendingTargetY"), tag.getInt("PendingTargetZ"));
        } else {
            pendingTargetPos = null;
        }
        if (tag.contains("PendingRotation")) {
            pendingRotation = Rotation.valueOf(tag.getString("PendingRotation"));
        } else {
            pendingRotation = null;
        }
        if (tag.contains("PendingSchematicName")) {
            pendingSchematicName = tag.getString("PendingSchematicName");
        } else {
            pendingSchematicName = null;
        }
        if (tag.contains("PlacementDelayTicks")) {
            placementDelayTicks = tag.getInt("PlacementDelayTicks");
        } else {
            placementDelayTicks = -1;
        }

        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }

        // Console output
//        System.out.println("Loading PrinterBlockEntity at " + getBlockPos());
//        System.out.println("Loaded Schematic Name: " + storedSchematicName);
//        System.out.println("Loaded Target Position: " + storedTargetPos);
//        System.out.println("Loaded Rotation: " + storedRotation);
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
//        System.out.println("Set Schematic Name in PrinterBlockEntity at " + getBlockPos());
//        System.out.println("Schematic Name: " + storedSchematicName);
    }

    public void setTargetPos(BlockPos targetPos) {
        this.storedTargetPos = targetPos;
        setChanged(); // Mark the block entity as changed to save data
        if (level != null && !level.isClientSide()) {
            // Notify the client about the change
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        // Console output for debugging
//        System.out.println("Set Target Position in PrinterBlockEntity at " + getBlockPos());
//        System.out.println("Target Position: " + storedTargetPos);
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

        // Cancel any pending placement if the block is removed
        if (placementDelayTicks > 0) {
            placementDelayTicks = -1;
            pendingTargetPos = null;
            pendingRotation = null;
            pendingSchematicName = null;
            setChanged();
        }
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


    // Getter and Setter for clientPlacementDelayTicks
    public void setClientPlacementDelayTicks(int ticks) {
        this.clientPlacementDelayTicks = ticks;
    }


    public int getClientPlacementDelayTicks() {
        return clientPlacementDelayTicks;
    }


}
