package fr.thoridan.client.printer.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.thoridan.block.PrinterBlockEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.lang.reflect.Field;
public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

    private long lastMessageTime = 0;
    private static final long MESSAGE_COOLDOWN_MS = 0;
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

        // Load the schematic
        StructureTemplate template = loadStructure(blockEntity.getStoredSchematicName());
        if (template == null) {
            return;
        }

        // Get the target position and rotation
        BlockPos targetPos = blockEntity.getStoredTargetPos();
        Rotation rotation = blockEntity.getStoredRotation() != null ? blockEntity.getStoredRotation() : Rotation.NONE;

        // Render the structure
        renderStructure(template, blockEntity.getBlockPos(), targetPos, rotation, poseStack, bufferSource, combinedLight);
    }


    private StructureTemplate loadStructure(String schematicName) {
        // Get the schematics folder in the game directory
        File schematicsFolder = new File(Minecraft.getInstance().gameDirectory, "schematics");
        File schematicFile = new File(schematicsFolder, schematicName);

        if (!schematicFile.exists()) {
            System.out.println("Schematic file does not exist: " + schematicFile.getAbsolutePath());
            return null;
        }

        CompoundTag nbtData;
        try {
            // Read the NBT data from the file
            nbtData = NbtIo.readCompressed(new FileInputStream(schematicFile));
        } catch (IOException e) {
            System.out.println("Failed to read schematic file: " + e.getMessage());
            return null;
        }

        // Create a new StructureTemplate and load the NBT data
        StructureTemplate template = new StructureTemplate();

        // Obtain the HolderGetter<Block> from the Level's RegistryAccess
        HolderGetter<Block> holderGetter = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BLOCK);

        // Load the structure with the HolderGetter
        template.load(holderGetter, nbtData);

        return template;
    }

    private void renderStructure(StructureTemplate template, BlockPos blockEntityPos, BlockPos targetPos, Rotation rotation, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight) {
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

        // Use reflection to access the private 'palettes' field
        List<StructureTemplate.StructureBlockInfo> blockInfos;
        try {
            Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
            palettesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<StructureTemplate.Palette> palettes = (List<StructureTemplate.Palette>) palettesField.get(template);
            blockInfos = palettes.get(0).blocks();
            sendDebugMessage("BlockInfos size: " + blockInfos.size());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            poseStack.popPose();
            sendDebugMessage("Failed to access 'palettes' field in StructureTemplate");
            return;
        }

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


    private void renderTransparentBlock(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);

        // Set the desired alpha value (0.0F = fully transparent, 1.0F = fully opaque)
        float alpha = 0.5F; // Adjust this value as needed
        if(DEBUG){
            alpha = 1.0F;
        }


        // Use the translucent render type
        RenderType renderType = RenderType.translucent();

        // Get the original VertexConsumer
        VertexConsumer originalConsumer = bufferSource.getBuffer(renderType);

        // Wrap it with our AlphaAdjustingVertexConsumer
        VertexConsumer alphaConsumer = new AlphaAdjustingVertexConsumer(originalConsumer, alpha);

        // Render the block using the alpha-adjusted VertexConsumer
        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                alphaConsumer,
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
