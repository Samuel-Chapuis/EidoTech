package fr.thoridan.client.renderer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
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
import java.util.List;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

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

        // Calculate position and rotation
        BlockPos targetPos = blockEntity.getStoredTargetPos();
        Rotation rotation = blockEntity.getStoredRotation() != null ? blockEntity.getStoredRotation() : Rotation.NONE;

        // Render the structure
        renderStructure(template, targetPos, rotation, poseStack, bufferSource, combinedLight);
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

    private void renderStructure(StructureTemplate template, BlockPos pos, Rotation rotation, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight) {
        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }

        poseStack.pushPose();

        double dx = pos.getX() - Minecraft.getInstance().player.getX();
        double dy = pos.getY() - Minecraft.getInstance().player.getY();
        double dz = pos.getZ() - Minecraft.getInstance().player.getZ();

        poseStack.translate(dx, dy, dz);

        StructurePlaceSettings placeSettings = new StructurePlaceSettings().setRotation(rotation);

        // Use reflection to access the private 'palettes' field
        List<StructureTemplate.StructureBlockInfo> blockInfos;
        try {
            Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
            palettesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<StructureTemplate.Palette> palettes = (List<StructureTemplate.Palette>) palettesField.get(template);
            blockInfos = palettes.get(0).blocks();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            poseStack.popPose();
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
            BlockPos rotatedPos = StructureTemplate.calculateRelativePosition(placeSettings, localPos).subtract(pos);

            // Push matrix for the block
            poseStack.pushPose();

            // Translate to block position
            poseStack.translate(rotatedPos.getX(), rotatedPos.getY(), rotatedPos.getZ());

            // Render the block
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, combinedLight, OverlayTexture.NO_OVERLAY);

            // Pop matrix for the block
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PrinterBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
