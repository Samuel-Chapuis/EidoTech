package fr.thoridan.client.printer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.thoridan.Techutilities;
import fr.thoridan.block.PrinterBlockEntity;
import fr.thoridan.client.printer.widget.TextButton;
import fr.thoridan.menu.PrinterMenu;
import fr.thoridan.network.ModNetworking;
import fr.thoridan.network.printer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Techutilities.MODID, "textures/gui/printer_gui.png");
    private static final ResourceLocation SECOND_TEXTURE = new ResourceLocation(Techutilities.MODID, "textures/gui/second_image.png");
    private static final int MAX_DISTANCE_ALLOWED = 50;
    private static final int MAX_BLOCKS = 11000;
    private static final long VALIDATION_DELAY_MS = 500; // half-second
    private boolean needsValidation = false;
    private long lastChangeTime = 0L;
    private List<String> schematics = new ArrayList<>();
    private List<TextButton> schematicButtons = new ArrayList<>();
    private Map<Item, Integer> missingItems = Collections.emptyMap();
    private EditBox posXField, posYField, posZField;
    private CycleButton<Integer> rotationButton;
    private String selectedSchematicName;
    private int selectedIndex = -1;
    private boolean notEnoughEnergy = false;
    private EditBox filePathField;

    // Adjusted widths & heights to accommodate extra UI
    public PrinterScreen(PrinterMenu menu, Inventory inv, Component titleIn) {
        super(menu, inv, titleIn);
        this.imageWidth = 8 + (12 * 18) + 8;
        this.imageHeight = 18 + (7 * 18) + 4 + (3 * 18) + 4 + 18 + 4 + 28;
        loadSchematics();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int inputFieldW = 50, inputFieldH = 20;
        int posFieldY = topPos + inputFieldH + 15;

        // Position Fields (X, Y, Z)
        posXField = new EditBox(font, leftPos - 3 * (inputFieldW + 10), posFieldY, inputFieldW, inputFieldH, Component.literal("X"));
        posYField = new EditBox(font, leftPos - 2 * (inputFieldW + 10), posFieldY, inputFieldW, inputFieldH, Component.literal("Y"));
        posZField = new EditBox(font, leftPos - 1 * (inputFieldW + 10), posFieldY, inputFieldW, inputFieldH, Component.literal("Z"));
        posXField.setResponder(v -> onPositionFieldChange());
        posYField.setResponder(v -> onPositionFieldChange());
        posZField.setResponder(v -> onPositionFieldChange());
        addRenderableWidget(posXField);
        addRenderableWidget(posYField);
        addRenderableWidget(posZField);

        var blockEntity = menu.getBlockEntity();
        if (blockEntity.getStoredTargetPos() != null) {
            var storedPos = blockEntity.getStoredTargetPos();
            posXField.setValue(String.valueOf(storedPos.getX()));
            posYField.setValue(String.valueOf(storedPos.getY()));
            posZField.setValue(String.valueOf(storedPos.getZ()));
        }

        selectedSchematicName = blockEntity.getStoredSchematicName();
        selectedIndex = (selectedSchematicName != null) ? schematics.indexOf(selectedSchematicName) : -1;

        int initialDeg = (blockEntity.getStoredRotation() != null) ?
                getDegreesFromRotation(blockEntity.getStoredRotation()) : 0;

        // Schematic buttons
        createSchematicButtons();
        updateSchematicButtonColors();

        // Rotation cycle
        rotationButton = CycleButton.<Integer>builder(degrees -> Component.literal(degrees + "°"))
                .withValues(0, 90, 180, 270)
                .displayOnlyValue()
                .withInitialValue(initialDeg)
                .create(leftPos - 9 - inputFieldW, topPos + 5, inputFieldW, inputFieldH, Component.literal("Rotation"),
                        (btn, val) -> {
                            Rotation rot = switch (val) {
                                case 90 -> Rotation.CLOCKWISE_90;
                                case 180 -> Rotation.CLOCKWISE_180;
                                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                                default -> Rotation.NONE;
                            };
                            ModNetworking.INSTANCE.sendToServer(new RotationChangePacket(blockEntity.getBlockPos(), rot));
                        });
        addRenderableWidget(rotationButton);

        // Place structure
        addRenderableWidget(Button.builder(Component.literal("Place Structure"), b -> sendPlaceStructurePacket())
                .bounds(leftPos - (100 + 31 + inputFieldW), topPos + 5, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderTooltip(guiGraphics, mouseX, mouseY);
        validatePositionIfNeeded();

        preparePopupRendering();

        if (!missingItems.isEmpty()) renderMissingItemsPopup(guiGraphics);
        if (notEnoughEnergy) renderNotEnoughEnergyPopup(guiGraphics);

        int placementDelay = menu.getBlockEntity().getClientPlacementDelayTicks();
        if (placementDelay > 0) renderPlacementDelayPopup(guiGraphics, placementDelay);

        restoreRenderingState();

        if (isMouseOverEnergyBar(mouseX, mouseY)) {
            int e = menu.getBlockEntity().getEnergyStored();
            int m = menu.getBlockEntity().getMaxEnergyStored();
            guiGraphics.renderComponentTooltip(font, List.of(Component.literal(e + " / " + m + " FE")), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        RenderSystem.setShaderTexture(0, SECOND_TEXTURE);
        guiGraphics.blit(SECOND_TEXTURE, leftPos - 185, topPos, 0, 0, 180, imageHeight);

        renderEnergyBar(guiGraphics, menu.getBlockEntity().getEnergyStored(), menu.getBlockEntity().getMaxEnergyStored());
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 4210752, false);

        int sepY = 18 + (7 * 18) + 4 + 6;
        guiGraphics.hLine(8, imageWidth - 8, sepY, 0xFF404040);
        guiGraphics.drawString(font, this.playerInventoryTitle, 8, sepY + 8, 4210752, false);
    }

    @Override
    public void removed() {
        super.removed();
        validatePosition();
        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            ModNetworking.INSTANCE.sendToServer(new PositionUpdatePacket(
                    menu.getBlockEntity().getBlockPos(), new BlockPos(x, y, z)));
        } catch (NumberFormatException ignored) {}
    }

    // --------------------------------------------------
    //                 SCHEMATICS
    // --------------------------------------------------

    private void loadSchematics() {
        File folder = new File(Minecraft.getInstance().gameDirectory, "schematics");
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((d, name) -> name.endsWith(".schematic") || name.endsWith(".nbt"));
        if (files == null) return;

        for (File f : files) {
            try (FileInputStream fis = new FileInputStream(f)) {
                CompoundTag nbt = NbtIo.readCompressed(fis);
                if (nbt != null && nbt.contains("blocks", Tag.TAG_LIST)) {
                    ListTag blocksTag = nbt.getList("blocks", Tag.TAG_COMPOUND);
                    if (blocksTag.size() <= MAX_BLOCKS) {
                        schematics.add(f.getName());
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private void createSchematicButtons() {
        schematicButtons.clear();
        int startY = topPos + 120;
        int buttonHeight = 10;
        int x = leftPos - 100 - 31 - 50;

        for (int i = 0; i < schematics.size(); i++) {
            var index = i;
            var name = schematics.get(i);
            var text = Component.literal(name);
            int textWidth = font.width(text);
            int color = (i == selectedIndex) ? 0xFFFF00 : 0xFFFFFF;

            var button = new TextButton(x, startY + i * (buttonHeight + 2), textWidth, buttonHeight, text, b -> {
                // 1) Update selection UI
                selectedIndex = index;
                selectedSchematicName = name;
                updateSchematicButtonColors();

                // 2) Immediately upload schematic from client's local folder
                uploadSchematicFromClient(selectedSchematicName);

                // 3) Optionally send a SchematicSelectionPacket
                //    (assuming the server also expects to store the name or do something else)
                ModNetworking.INSTANCE.sendToServer(new SchematicSelectionPacket(
                        menu.getBlockEntity().getBlockPos(),
                        selectedSchematicName
                ));

            }, color);

            schematicButtons.add(button);
            addRenderableWidget(button);
        }
    }


    private void uploadSchematicFromClient(String schematicName) {
        // 1) Find the local file in the "schematics" folder
        File file = new File(Minecraft.getInstance().gameDirectory, "schematics/" + schematicName);
        if (!file.exists()) {
            System.out.println("Schematic file not found on client: " + file.getAbsolutePath());
            Techutilities.broadcastServerMessage("Schematic file not found on client: " + file.getAbsolutePath(), false);
            return;
        }

        // 2) Read its bytes
        byte[] fileBytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            fileBytes = fis.readAllBytes(); // Java 9+
        } catch (IOException e) {
            System.out.println("Error reading schematic file: " + e.getMessage());
            Techutilities.broadcastServerMessage("Error reading schematic file: " + e.getMessage(), false);
            return;
        }

        // 3) (Optional) compress if needed. If .nbt is already compressed, you might skip this.
        //    byte[] fileBytes = MyCompressionUtils.compress(originalBytes);

        // 4) Split the byte[] into manageable chunks
        final int CHUNK_SIZE = 32 * 1024; // 32 KB
        int totalChunks = (fileBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;

        // 5) Send each chunk to server
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, fileBytes.length);
            byte[] chunkData = Arrays.copyOfRange(fileBytes, start, end);

            // This is your custom packet class that handles uploading
            ModNetworking.INSTANCE.sendToServer(new UploadSchematicPacket(
                    schematicName, // or you might just pass the file's base name
                    i,
                    totalChunks,
                    chunkData
            ));
        }

        System.out.println("Schematic upload initiated for " + schematicName);
    }


    private void updateSchematicButtonColors() {
        for (int i = 0; i < schematicButtons.size(); i++) {
            var b = schematicButtons.get(i);
            b.setTextColor(i == selectedIndex ? 0xFFFF00 : 0xFFFFFF);
        }
    }

    // --------------------------------------------------
    //              PLACEMENT & ROTATION
    // --------------------------------------------------

    private void sendPlaceStructurePacket() {
        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            int deg = rotationButton.getValue();
            Rotation rot = switch (deg) {
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            if (selectedSchematicName == null) return;
            ModNetworking.INSTANCE.sendToServer(new PlaceStructurePacket(
                    menu.getBlockEntity().getBlockPos(), x, y, z, rot, selectedSchematicName));
        } catch (NumberFormatException ignored) {}
    }

    private int getDegreesFromRotation(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
            default -> 0;
        };
    }

    // --------------------------------------------------
    //                 ENERGY BAR
    // --------------------------------------------------

    private void renderEnergyBar(GuiGraphics guiGraphics, int energy, int maxEnergy) {
        int barX = leftPos + 200, barY = topPos + 170, w = 8, h = 80;
        double ratio = (maxEnergy > 0) ? (double) energy / maxEnergy : 0.0;
        int filled = (int) (ratio * h);
        int fillY = barY + (h - filled);
        guiGraphics.fill(barX, fillY, barX + w, barY + h, 0xFFFFFFCC);
    }

    private boolean isMouseOverEnergyBar(int mouseX, int mouseY) {
        int x = leftPos + 200, y = topPos + 170, w = 8, h = 80;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // --------------------------------------------------
    //                  POPUPS
    // --------------------------------------------------

    public void setMissingItems(Map<Item, Integer> missingItems) {
        this.missingItems = missingItems;
    }

    public void displayNotEnoughEnergyPopup() {
        this.notEnoughEnergy = true;
    }

    private void renderMissingItemsPopup(GuiGraphics guiGraphics) {
        int minW = 150, pad = 6;
        int titleWidth = font.width("Missing Items:");
        int maxW = titleWidth;
        for (var e : missingItems.entrySet()) {
            int w = font.width(e.getValue() + " x " + e.getKey().getDescription().getString());
            if (w > maxW) maxW = w;
        }
        int popupW = Math.max(minW, maxW + pad * 2);
        int lineH = 7, titleH = 10;
        int popupH = titleH + (missingItems.size() * lineH) + pad * 2;
        int px = (int) (width*0.04), py = (int) (height*0.305);

        // Decide on a scale factor:
        float scale = 0.75F;

        // Title (scaled):
        guiGraphics.pose().pushPose();
        {
            // Translate first, so that text is positioned where you want it
            guiGraphics.pose().translate(px + pad, py + pad, 0);
            // Apply your chosen scale
            guiGraphics.pose().scale(scale, scale, 1.0F);

            // Now we draw at (0, 0) because we've already done the translate above.
            guiGraphics.drawString(font, "Missing Items:", 0, 0, 0xFFFFFF, false);
        }
        guiGraphics.pose().popPose();

        int counter = 0;
        // Then your text lines, maybe also scaled:
        int textY = py + pad + titleH;  // This is your "normal" Y at full scale
        for (var e : missingItems.entrySet()) {
            String txt = e.getValue() + " x " + e.getKey().getDescription().getString();

            guiGraphics.pose().pushPose();
            {
                // Move the position for each line
                guiGraphics.pose().translate(px + pad, textY, 0);
                guiGraphics.pose().scale(scale, scale, 1.0F);

                guiGraphics.drawString(font, txt, 0, 0, 0xFFFFFF, false);
            }
            guiGraphics.pose().popPose();

            textY += lineH; // next line

            counter++;
            if (counter == 3) {
                guiGraphics.pose().translate(px + pad, textY, 0);
                guiGraphics.pose().scale(scale, scale, 1.0F);
                guiGraphics.drawString(font, "And more...", 0, 0, 0xFFFFFF, false);
                break;
            }
        }
    }


    private void renderNotEnoughEnergyPopup(GuiGraphics guiGraphics) {
        // The two lines we want to show
        String line1 = "Not enough energy";
        String line2 = "to place the structure!";

        // Decide on a scale factor (same as you did in missing-items)
        float scale = 0.75F;
        int pad = 6;      // Horizontal padding
        int lineH = 10;   // Vertical distance between lines (at full scale)

        // Figure out the longest line to decide how wide the popup should be
        int line1Width = font.width(line1);
        int line2Width = font.width(line2);
        int maxWidth    = Math.max(line1Width, line2Width);

        // Minimum popup width so it doesn’t get too narrow
        int minWidth  = 150;
        int popupW    = Math.max(minWidth, maxWidth + pad * 2);
        // We have 2 lines plus some vertical padding
        int popupH    = lineH * 2 + pad * 2;

        // Position similar to your missing-items method
        // (You can change these multipliers to match your UI design.)
        int px = (int) (width*0.04), py = (int) (height*0.305);

        // Draw first line, scaled
        guiGraphics.pose().pushPose();
        {
            // Move to top-left of popup, then add padding
            guiGraphics.pose().translate(px + pad, py + pad, 0);
            // Scale everything inside
            guiGraphics.pose().scale(scale, scale, 1.0F);

            // Draw line1 at (0,0) because we already translated above
            guiGraphics.drawString(font, line1, 0, 0, 0xFFFFFF, false);
        }
        guiGraphics.pose().popPose();

        // Draw second line, scaled
        guiGraphics.pose().pushPose();
        {
            // Move down by lineH at full scale (before scaling)
            guiGraphics.pose().translate(px + pad, py + pad + lineH, 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);

            guiGraphics.drawString(font, line2, 0, 0, 0xFFFFFF, false);
        }
        guiGraphics.pose().popPose();
    }

    private void renderPlacementDelayPopup(GuiGraphics guiGraphics, int ticks) {
        // Compute remaining time
        int   sec  = ticks / 20;
        float frac = (ticks % 20) / 20f;
        String timeText = String.format("Time remaining: %.1fs", sec + frac);

        // Title
        String title = "Placing Structure...";

        // Scale & layout settings
        float scale = 0.75F;
        int pad     = 6;
        int lineH   = 10;

        // Compute max width among the lines we’ll display
        int titleWidth = font.width(title);
        int timeWidth  = font.width(timeText);
        int maxWidth   = Math.max(titleWidth, timeWidth);

        int minWidth   = 150;
        int popupW     = Math.max(minWidth, maxWidth + pad * 2);
        // 2 lines => 2 * lineH plus top/bottom pad
        int popupH     = lineH * 2 + pad * 2;

        // Position it somewhere similar to your Missing Items popup
        int px = (int) (width*0.04), py = (int) (height*0.305);

        // Draw the title
        guiGraphics.pose().pushPose();
        {
            guiGraphics.pose().translate(px + pad, py + pad, 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);

            guiGraphics.drawString(font, title, 0, 0, 0xFFFFFF, false);
        }
        guiGraphics.pose().popPose();

        // Draw the time remaining
        guiGraphics.pose().pushPose();
        {
            guiGraphics.pose().translate(px + pad, py + pad + lineH, 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);

            guiGraphics.drawString(font, timeText, 0, 0, 0xFFFFFF, false);
        }
        guiGraphics.pose().popPose();
    }

    // --------------------------------------------------
    //       POSITION VALIDATION & RENDER UTILS
    // --------------------------------------------------

    private void preparePopupRendering() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private void restoreRenderingState() {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private void onPositionFieldChange() {
        needsValidation = true;
        lastChangeTime = System.currentTimeMillis();
    }

    private void validatePositionIfNeeded() {
        if (!needsValidation) return;
        if (System.currentTimeMillis() - lastChangeTime >= VALIDATION_DELAY_MS) {
            validatePosition();
            needsValidation = false;
        }
    }

    private void validatePosition() {
        var bePos = menu.getBlockEntity().getBlockPos();
        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            double dist = bePos.distToCenterSqr(x + 0.5, y + 0.5, z + 0.5);
            // If distance is too large, revert
            if (Math.sqrt(dist) > MAX_DISTANCE_ALLOWED) {
                posXField.setValue(String.valueOf(bePos.getX()));
                posYField.setValue(String.valueOf(bePos.getY()));
                posZField.setValue(String.valueOf(bePos.getZ()));
            }
        } catch (NumberFormatException e) {
            posXField.setValue(String.valueOf(bePos.getX()));
            posYField.setValue(String.valueOf(bePos.getY()));
            posZField.setValue(String.valueOf(bePos.getZ()));
        }
    }
}
