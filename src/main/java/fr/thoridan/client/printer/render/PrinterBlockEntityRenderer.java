package fr.thoridan.client.printer.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

    private long lastMessageTime = 0;
    private static final long MESSAGE_COOLDOWN_MS = 1000; // Adjust as needed
    private static final boolean DEBUG = true;

    public PrinterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // Constructor can be empty or store context if needed
    }

    @Override
    public void render(PrinterBlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        if (blockEntity.getStoredSchematicName() == null || blockEntity.getStoredTargetPos() == null) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        // Load the schematic blocks
        List<StructureTemplate.StructureBlockInfo> blockInfos = loadStructureBlocks(blockEntity.getStoredSchematicName());
        if (blockInfos == null || blockInfos.isEmpty()) {
            return;
        }

        // Get the target position and rotation
        BlockPos targetPos = blockEntity.getStoredTargetPos();
        Rotation rotation = blockEntity.getStoredRotation() != null ? blockEntity.getStoredRotation() : Rotation.NONE;

        // Render the structure
        renderStructure(blockInfos, blockEntity.getBlockPos(), targetPos, rotation, poseStack, bufferSource, combinedLight);
    }

    /**
     * Loads the schematic file and extracts the list of StructureBlockInfo.
     *
     * @param schematicName The name of the schematic file.
     * @return A list of StructureBlockInfo or null if loading fails.
     */
    @Nullable
    private List<StructureTemplate.StructureBlockInfo> loadStructureBlocks(String schematicName) {
        // Get the schematics folder in the game directory
        File schematicsFolder = new File(Minecraft.getInstance().gameDirectory, "schematics");
        File schematicFile = new File(schematicsFolder, schematicName);

        if (!schematicFile.exists()) {
            sendDebugMessage("Schematic file does not exist: " + schematicFile.getAbsolutePath());
            return null;
        }

        CompoundTag nbtData;
        try (FileInputStream fis = new FileInputStream(schematicFile)) {
            // Read the NBT data from the file
            nbtData = NbtIo.readCompressed(fis);
        } catch (IOException e) {
            sendDebugMessage("Failed to read schematic file: " + e.getMessage());
            return null;
        }

        // Extract the palette
        ListTag paletteList = nbtData.getList("palette", 10); // 10 for CompoundTag
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteList.size(); i++) {
            CompoundTag blockStateTag = paletteList.getCompound(i);
            String blockName = blockStateTag.getString("Name");
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
            if (block == null) {
                sendDebugMessage("Unknown block: " + blockName + ". Defaulting to AIR.");
                block = Blocks.AIR;
            }
            BlockState state = block.defaultBlockState();

            // Handle block properties if any
            if (blockStateTag.contains("Properties", 10)) { // 10 for CompoundTag
                CompoundTag properties = blockStateTag.getCompound("Properties");
                for (String key : properties.getAllKeys()) {
                    String value = properties.getString(key);
                    Property<?> property = getProperty(state, key);
                    if (property != null) {
                        // Use the helper method to set the property
                        state = setBlockStateProperty(state, property, value, blockName);
                    } else {
                        sendDebugMessage("Property '" + key + "' not found for block '" + blockName + "'.");
                    }
                }
            }

            palette.add(state);
        }

        // Extract the blocks
        ListTag blocksList = nbtData.getList("blocks", 10); // 10 for CompoundTag
        List<StructureTemplate.StructureBlockInfo> blockInfos = new ArrayList<>();

        for (int i = 0; i < blocksList.size(); i++) {
            CompoundTag blockTag = blocksList.getCompound(i);

            // Extract position
            CompoundTag posTag = blockTag.getCompound("pos");
            int x = posTag.getInt("x");
            int y = posTag.getInt("y");
            int z = posTag.getInt("z");
            BlockPos pos = new BlockPos(x, y, z);

            // Extract state index
            int stateIndex = blockTag.getInt("state"); // "state" is an integer index into the palette
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                sendDebugMessage("Invalid state index: " + stateIndex + " at block " + pos);
                continue;
            }
            BlockState state = palette.get(stateIndex);

            // Extract NBT if present
            @Nullable CompoundTag nbt = blockTag.contains("nbt", 10) ? blockTag.getCompound("nbt") : null;

            StructureTemplate.StructureBlockInfo blockInfo = new StructureTemplate.StructureBlockInfo(pos, state, nbt);
            blockInfos.add(blockInfo);
        }

        sendDebugMessage("Loaded " + blockInfos.size() + " blocks from schematic '" + schematicName + "'.");
        return blockInfos;
    }

    /**
     * Retrieves the Property object for a given BlockState and property name.
     *
     * @param state The BlockState.
     * @param name  The property name.
     * @return The Property object or null if not found.
     */
    @Nullable
    private <T extends Comparable<T>> Property<T> getProperty(BlockState state, String name) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(name)) {
                @SuppressWarnings("unchecked")
                Property<T> typedProp = (Property<T>) prop;
                return typedProp;
            }
        }
        return null;
    }

    /**
     * Sets a Property value on a BlockState.
     *
     * @param state      The original BlockState.
     * @param property   The Property to set.
     * @param value      The string value to parse and set.
     * @param blockName  The name of the block (for debugging purposes).
     * @return The updated BlockState.
     */
    private <T extends Comparable<T>> BlockState setBlockStateProperty(BlockState state, Property<T> property, String value, String blockName) {
        Optional<T> parsedValue = property.getValue(value);
        if (parsedValue.isPresent()) {
            return state.setValue(property, parsedValue.get());
        } else {
            sendDebugMessage("Invalid value '" + value + "' for property '" + property.getName() + "' on block '" + blockName + "'.");
            return state;
        }
    }

    /**
     * Renders the structure based on the provided block information.
     *
     * @param blockInfos     List of StructureBlockInfo to render.
     * @param blockEntityPos Position of the block entity.
     * @param targetPos      Target position where the structure should be rendered.
     * @param rotation       Rotation to apply to the structure.
     * @param poseStack      The PoseStack for rendering transformations.
     * @param bufferSource   The buffer source for rendering.
     * @param combinedLight  Light level for rendering.
     */
    private void renderStructure(List<StructureTemplate.StructureBlockInfo> blockInfos, BlockPos blockEntityPos, BlockPos targetPos, Rotation rotation, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight) {
        sendDebugMessage("Hello from PrinterBlockEntityRenderer -> renderStructure");

        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }
        sendDebugMessage("Client level is not null");

        poseStack.pushPose();

        // Calculate the offset from the block entity position to the target position
        double offsetX = targetPos.getX() - blockEntityPos.getX();
        double offsetY = targetPos.getY() - blockEntityPos.getY();
        double offsetZ = targetPos.getZ() - blockEntityPos.getZ();

        // Translate pose stack by the offset to the target position
        poseStack.translate(offsetX, offsetY, offsetZ);

        StructurePlaceSettings placeSettings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true);

        sendDebugMessage("BlockInfos size: " + blockInfos.size());

        // Render each block in the structure
        for (StructureTemplate.StructureBlockInfo blockInfo : blockInfos) {
            BlockState state = blockInfo.state();
            if (state.isAir()) {
                continue;
            }

            BlockPos localPos = blockInfo.pos();

            // Apply rotation to local position
            BlockPos rotatedPos = StructureTemplate.calculateRelativePosition(placeSettings, localPos);

            // Rotate the block state
            BlockState rotatedState = state.rotate(placeSettings.getRotation());

            // Push matrix for the block
            poseStack.pushPose();

            // Translate to block position within the structure
            poseStack.translate(rotatedPos.getX(), rotatedPos.getY(), rotatedPos.getZ());

            // Render the block with translucency
            renderTransparentBlock(rotatedState, poseStack, bufferSource, combinedLight);

            // Pop matrix for the block
            poseStack.popPose();
        }

        poseStack.popPose();

        // If bufferSource is a BufferSource, flush the buffers
        if (bufferSource instanceof MultiBufferSource.BufferSource) {
            ((MultiBufferSource.BufferSource) bufferSource).endBatch();
        }
    }

    /**
     * Renders a single block with optional transparency.
     *
     * @param state          The BlockState to render.
     * @param poseStack      The PoseStack for rendering transformations.
     * @param bufferSource   The buffer source for rendering.
     * @param combinedLight  Light level for rendering.
     */
    private void renderTransparentBlock(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);

        // Set the desired alpha value (0.0F = fully transparent, 1.0F = fully opaque)
        float alpha = DEBUG ? 1.0F : 0.5F; // Adjust this value as needed

        // Choose the appropriate RenderType based on alpha
        RenderType renderType = alpha < 1.0F ? RenderType.translucent() : RenderType.solid();

        // Get the original VertexConsumer
        VertexConsumer originalConsumer = bufferSource.getBuffer(renderType);

        // Wrap it with our AlphaAdjustingVertexConsumer if transparency is needed
        VertexConsumer consumer = alpha < 1.0F ? new AlphaAdjustingVertexConsumer(originalConsumer, alpha) : originalConsumer;

        // Render the block using the adjusted VertexConsumer
        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                state,
                model,
                1.0F, 1.0F, 1.0F, // RGB colors
                combinedLight,
                OverlayTexture.NO_OVERLAY
        );
    }

    @Override
    public boolean shouldRenderOffScreen(PrinterBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /**
     * Sends a debug message to the player if debugging is enabled.
     *
     * @param message The message to send.
     */
    private void sendDebugMessage(String message) {
        if (!DEBUG) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMessageTime < MESSAGE_COOLDOWN_MS) {
            return; // Skip sending the message to avoid spamming
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
            lastMessageTime = currentTime;
        }
    }
}