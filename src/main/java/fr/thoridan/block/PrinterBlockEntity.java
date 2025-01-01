package fr.thoridan.block;

import com.mojang.authlib.GameProfile;
import fr.thoridan.Techutilities;
import fr.thoridan.energy.CustomEnergyStorage;
import fr.thoridan.menu.CustomItemStackHandler;
import fr.thoridan.network.ModNetworking;
import fr.thoridan.network.printer.MissingItemsPacket;
import fr.thoridan.network.printer.NotEnoughEnergyPacket;
import fr.thoridan.network.printer.PlacementDelayUpdatePacket;
import fr.thoridan.network.printer.UploadSchematicPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class PrinterBlockEntity extends BlockEntity {
    private UUID ownerUUID;
    private BlockPos pendingTargetPos;
    private Rotation pendingRotation;
    private String pendingSchematicName;
    private BlockPos storedTargetPos;
    private Rotation storedRotation;
    private String storedSchematicName;

    private int placementDelayTicks = -1;
    private int clientPlacementDelayTicks = -1;
    private double tick_per_block = 3;
    private int energy_per_block = 1000;

    // Energy
    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(100000000, this::onEnergyChanged);
    private final LazyOptional<IEnergyStorage> lazyEnergyHandler = LazyOptional.of(() -> energyStorage);

    // Schematic data loaded once, reused for item-check & placement
    private CompoundTag loadedSchematicNbt;
    private List<BlockState> loadedPalette;
    private ListTag loadedBlocksTag;

    private final CustomItemStackHandler itemHandler = new CustomItemStackHandler(84) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };
    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER_BLOCK_ENTITY.get(), pos, state);
    }

    // -----------------------------------------------------
    //                  MAIN LOGIC
    // -----------------------------------------------------

    /**
     * Schedules structure placement after verifying items and energy.
     */
    public void placeStructureAt(BlockPos targetPos, Rotation rotation, String schematicName, ServerPlayer player) {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            Techutilities.broadcastServerMessage("Cannot place structure on client side.", false);
            return;
        }
        Techutilities.broadcastServerMessage("Placing structure...", false);

        // If a placement is already in progress, notify player
        if (placementDelayTicks > 0) {
            Techutilities.broadcastServerMessage("A structure placement is already in progress.", false);
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            Techutilities.broadcastServerMessage("Cannot place structure on client side.", false);
            return;
        }
        Techutilities.broadcastServerMessage("Server level found.", false);

        // Load schematic data once (palette + blocks)
        if (!loadSchematicData(schematicName, serverLevel)) {
            Techutilities.broadcastServerMessage("Failed to load schematic data.", false);
            return;
        }
        Techutilities.broadcastServerMessage("Schematic data loaded.", false);

        // Calculate required items
        Map<Item, Integer> requiredItems = calculateRequiredItems(rotation, loadedPalette, loadedBlocksTag);
        int totalBlocks = loadedBlocksTag.size();
        int energyRequired = totalBlocks * energy_per_block;

        // Check if there's enough energy
        if (energyStorage.getEnergyStored() < energyRequired) {
            ModNetworking.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new NotEnoughEnergyPacket());
            Techutilities.broadcastServerMessage("Not enough energy to place the structure.", false);
            return;
        }
        Techutilities.broadcastServerMessage("Energy check passed.", false);

        // Check if there's enough items
        Map<Item, Integer> missingItems = getMissingItems(requiredItems);
        if (!missingItems.isEmpty()) {
            sendMissingItemsToClient(missingItems, player);
            Techutilities.broadcastServerMessage("Not enough items to place the structure.", false);
            return;
        }
        Techutilities.broadcastServerMessage("Item check passed.", false);

        // Consume items & energy
        consumeItems(requiredItems);
        energyStorage.extractEnergy(energyRequired, false);

        // Schedule placement
        pendingTargetPos = targetPos;
        pendingRotation = rotation;
        pendingSchematicName = schematicName;
        placementDelayTicks = (int) (totalBlocks * tick_per_block);
        Techutilities.broadcastServerMessage("Structure placement scheduled. It will takes" + tick_per_block + " ticks", false);
        setChanged();
    }

    /**
     * Actually places all blocks, called after the delay finishes in {@link #tick}.
     */
    private void performStructurePlacement() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        Techutilities.broadcastServerMessage("Perform structure placement", false);

        // Create a FakePlayer with SURVIVAL mode
        GameProfile ownerProfile = new GameProfile(ownerUUID, "[PrinterOwner]");
        FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, ownerProfile);
        fakePlayer.setGameMode(GameType.SURVIVAL);
        Techutilities.broadcastServerMessage("Fake player created", false);

        // We already have loadedSchematicNbt, loadedPalette, loadedBlocksTag
        if (loadedSchematicNbt == null || loadedPalette == null || loadedBlocksTag == null) {
            // If for some reason they are null, bail
            resetPlacement();
            return;
        }
        Techutilities.broadcastServerMessage("Schematic data loaded", false);

        // Place each block
        for (int i = 0; i < loadedBlocksTag.size(); i++) {
            CompoundTag blockTag = loadedBlocksTag.getCompound(i);

            // Original block position
            ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
            BlockPos relPos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));

            // Get rotated/mirrored state
            BlockState original = loadedPalette.get(blockTag.getInt("state"));
            BlockState rotated = original.mirror(Mirror.NONE).rotate(pendingRotation);
            BlockPos worldPos = transformBlockPos(relPos, pendingRotation).offset(pendingTargetPos);

            // Grab tile entity nbt if any
            CompoundTag beNbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt") : null;

            // Simulate block placement
            simulateBlockPlacement(fakePlayer, serverLevel, rotated, worldPos, beNbt);
        }

        // Done -> reset
        resetPlacement();
    }

    /**
     * Called each server tick. Decrements placement delay and triggers final placement.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, PrinterBlockEntity be) {
        if (be.placementDelayTicks > 0) {
            be.placementDelayTicks--;
            if (be.placementDelayTicks == 0) {
                be.performStructurePlacement();
                be.setChanged();
            }
            if (level instanceof ServerLevel serverLevel) {
                ModNetworking.INSTANCE.send(
                        PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(pos)),
                        new PlacementDelayUpdatePacket(pos, be.placementDelayTicks)
                );
            }
        }
    }

    // -----------------------------------------------------
    //            SCHEMATIC LOADING/ITEM CHECK
    // -----------------------------------------------------

    /**
     * Loads schematic data (NBT, palette, blocks) from a file in the "schematics" folder.
     */
    private boolean loadSchematicData(String schematicName, ServerLevel serverLevel) {
        File folder = new File(FMLPaths.GAMEDIR.get().toFile(), "schematics");
        File file = new File(folder, schematicName);
        if (!file.exists()) return false; // Schematic not found

        try (FileInputStream fis = new FileInputStream(file)) {
            loadedSchematicNbt = NbtIo.readCompressed(fis);
        } catch (IOException e) {
            return false;
        }

        // Load palette
        HolderGetter<Block> holderGetter = serverLevel.registryAccess().lookupOrThrow(Registries.BLOCK);
        loadedPalette = new ArrayList<>();
        ListTag paletteTag = loadedSchematicNbt.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag stateTag = paletteTag.getCompound(i);
            loadedPalette.add(NbtUtils.readBlockState(holderGetter, stateTag));
        }

        // Load blocks
        loadedBlocksTag = loadedSchematicNbt.getList("blocks", Tag.TAG_COMPOUND);
        return true;
    }

    private void uploadSchematicFromClient(String filePath, String schematicName) {
        File file = new File(filePath);

        // 1) Read raw bytes from the file
        byte[] fileBytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            fileBytes = fis.readAllBytes();
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return;
        }

        // 2) (Optional) Re-compress or confirm it's compressed if needed
        //    For example, if your file is not already compressed, you might do:
        //    fileBytes = MyCompressionUtils.compress(fileBytes);

        // 3) Split into chunks (e.g. 32 KB each)
        final int CHUNK_SIZE = 32 * 1024;
        int totalChunks = (fileBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE; // ceiling division

        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, fileBytes.length);
            byte[] chunkData = Arrays.copyOfRange(fileBytes, start, end);

            // 4) Send each chunk to the server
            ModNetworking.INSTANCE.sendToServer(new UploadSchematicPacket(
                    schematicName,  // or file.getName()
                    i,
                    totalChunks,
                    chunkData
            ));
        }

        // At this point, the server should accumulate the data
        // in something like SchematicManager.storeChunk(...).
    }


    /**
     * Returns how many of each item is required by rotating the block states and counting them.
     */
    private Map<Item, Integer> calculateRequiredItems(Rotation rotation, List<BlockState> palette, ListTag blocksTag) {
        Map<Item, Integer> required = new HashMap<>();
        for (int i = 0; i < blocksTag.size(); i++) {
            int stateId = blocksTag.getCompound(i).getInt("state");
            BlockState state = palette.get(stateId).rotate(rotation);
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                required.put(item, required.getOrDefault(item, 0) + 1);
            }
        }
        return required;
    }

    /**
     * Checks how many items are missing in the block inventory.
     */
    private Map<Item, Integer> getMissingItems(Map<Item, Integer> requiredItems) {
        Map<Item, Integer> inventoryItems = new HashMap<>();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                inventoryItems.put(item, inventoryItems.getOrDefault(item, 0) + stack.getCount());
            }
        }
        Map<Item, Integer> missing = new HashMap<>();
        for (Map.Entry<Item, Integer> e : requiredItems.entrySet()) {
            int available = inventoryItems.getOrDefault(e.getKey(), 0);
            if (available < e.getValue()) {
                missing.put(e.getKey(), e.getValue() - available);
            }
        }
        return missing;
    }

    /**
     * Removes the required items from the inventory.
     */
    private void consumeItems(Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            Item item = entry.getKey();
            int needed = entry.getValue();
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack slotStack = itemHandler.getStackInSlot(i);
                if (slotStack.getItem() == item) {
                    int toRemove = Math.min(slotStack.getCount(), needed);
                    slotStack.shrink(toRemove);
                    needed -= toRemove;
                    if (slotStack.isEmpty()) {
                        itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    if (needed <= 0) break;
                }
            }
        }
    }

    private void sendMissingItemsToClient(Map<Item, Integer> missing, ServerPlayer player) {
        ModNetworking.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MissingItemsPacket(missing));
    }

    // -----------------------------------------------------
    //            PLACEMENT HELPERS
    // -----------------------------------------------------

    /**
     * Transforms a local block-pos by the given rotation.
     */
    private BlockPos transformBlockPos(BlockPos pos, Rotation rotation) {
        return switch (rotation) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        };
    }

    /**
     * Simulates block placement via a FakePlayer using standard useItemOn logic.
     */
    private void simulateBlockPlacement(FakePlayer fakePlayer, ServerLevel level, BlockState blockState, BlockPos pos, @Nullable CompoundTag nbt) {
        ItemStack stack = new ItemStack(blockState.getBlock().asItem());
        if (stack.isEmpty()) return;

        stack.setCount(1);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        fakePlayer.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = fakePlayer.gameMode.useItemOn(fakePlayer, level, stack, InteractionHand.MAIN_HAND, hitResult);

        // If placement succeeded and there's tile entity data:
        if (result.consumesAction() && nbt != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) be.load(nbt);
        }
    }

    /**
     * Resets the block entity to 'no placement in progress'.
     */
    private void resetPlacement() {
        pendingTargetPos = null;
        pendingRotation = null;
        pendingSchematicName = null;
        loadedSchematicNbt = null;
        loadedPalette = null;
        loadedBlocksTag = null;
        placementDelayTicks = -1;
        setChanged();
    }

    // -----------------------------------------------------
    //           ENERGY CALLBACK
    // -----------------------------------------------------

    private void onEnergyChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // -----------------------------------------------------
    //              SAVE / LOAD
    // -----------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());

        // Stored user-chosen states
        if (storedTargetPos != null) {
            tag.putInt("TargetX", storedTargetPos.getX());
            tag.putInt("TargetY", storedTargetPos.getY());
            tag.putInt("TargetZ", storedTargetPos.getZ());
        }
        if (storedRotation != null) tag.putString("Rotation", storedRotation.name());
        if (storedSchematicName != null) tag.putString("SchematicName", storedSchematicName);

        // Pending placement
        if (pendingTargetPos != null) {
            tag.putInt("PendingTargetX", pendingTargetPos.getX());
            tag.putInt("PendingTargetY", pendingTargetPos.getY());
            tag.putInt("PendingTargetZ", pendingTargetPos.getZ());
        }
        if (pendingRotation != null) tag.putString("PendingRotation", pendingRotation.name());
        if (pendingSchematicName != null) tag.putString("PendingSchematicName", pendingSchematicName);

        tag.putInt("PlacementDelayTicks", placementDelayTicks);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("inventory"));

        if (tag.contains("TargetX")) {
            storedTargetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        storedRotation = tag.contains("Rotation") ? Rotation.valueOf(tag.getString("Rotation")) : null;
        storedSchematicName = tag.contains("SchematicName") ? tag.getString("SchematicName") : null;

        if (tag.contains("PendingTargetX")) {
            pendingTargetPos = new BlockPos(tag.getInt("PendingTargetX"), tag.getInt("PendingTargetY"), tag.getInt("PendingTargetZ"));
        }
        pendingRotation = tag.contains("PendingRotation") ? Rotation.valueOf(tag.getString("PendingRotation")) : null;
        pendingSchematicName = tag.contains("PendingSchematicName") ? tag.getString("PendingSchematicName") : null;
        placementDelayTicks = tag.contains("PlacementDelayTicks") ? tag.getInt("PlacementDelayTicks") : -1;

        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getInt("Energy"));
    }

    // -----------------------------------------------------
    //            GETTERS / SETTERS
    // -----------------------------------------------------

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    public int getEnergyStored() { return energyStorage.getEnergyStored(); }
    public int getMaxEnergyStored() { return energyStorage.getMaxEnergyStored(); }

    public BlockPos getStoredTargetPos() { return storedTargetPos; }
    public Rotation getStoredRotation() { return storedRotation; }
    public String getStoredSchematicName() { return storedSchematicName; }
    public void setRotation(Rotation rotation) {
        this.storedRotation = rotation;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
    public void setSchematicName(String name) {
        this.storedSchematicName = name;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
    public void setTargetPos(BlockPos pos) {
        this.storedTargetPos = pos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void setClientPlacementDelayTicks(int ticks) { this.clientPlacementDelayTicks = ticks; }
    public int getClientPlacementDelayTicks() { return clientPlacementDelayTicks; }

    @Override
    public AABB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }

    // -----------------------------------------------------
    //            CAPABILITIES & REMOVAL
    // -----------------------------------------------------

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return lazyItemHandler.cast();
        if (capability == ForgeCapabilities.ENERGY) return lazyEnergyHandler.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        lazyItemHandler.invalidate();
        lazyEnergyHandler.invalidate();
        // Cancel any pending placement if the block is removed
        if (placementDelayTicks > 0) {
            placementDelayTicks = -1;
            pendingTargetPos = null;
            pendingRotation = null;
            pendingSchematicName = null;
            setChanged();
        }
    }

    // Syncing with client
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }
    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }
}
