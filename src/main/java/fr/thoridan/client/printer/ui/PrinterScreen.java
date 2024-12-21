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
                selectedIndex = index;
                selectedSchematicName = name;
                updateSchematicButtonColors();
                ModNetworking.INSTANCE.sendToServer(new SchematicSelectionPacket(menu.getBlockEntity().getBlockPos(), selectedSchematicName));
            }, color);

            schematicButtons.add(button);
            addRenderableWidget(button);
        }
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
        int minW = 150, pad = 10;
        int titleWidth = font.width("Missing Items:");
        int maxW = titleWidth;
        for (var e : missingItems.entrySet()) {
            int w = font.width(e.getValue() + " x " + e.getKey().getDescription().getString());
            if (w > maxW) maxW = w;
        }
        int popupW = Math.max(minW, maxW + pad * 2);
        int lineH = 10, titleH = 15;
        int popupH = titleH + (missingItems.size() * lineH) + pad * 2;
        int px = (width - popupW) / 2, py = (height - popupH) / 2;

        drawPopupBox(guiGraphics, px, py, popupW, popupH);
        int textX = px + pad, textY = py + pad;
        guiGraphics.drawString(font, "Missing Items:", textX, textY, 0xFFFFFF, false);

        textY += titleH;
        for (var e : missingItems.entrySet()) {
            String txt = e.getValue() + " x " + e.getKey().getDescription().getString();
            guiGraphics.drawString(font, txt, textX, textY, 0xFFFFFF, false);
            textY += lineH;
        }
    }

    private void renderNotEnoughEnergyPopup(GuiGraphics guiGraphics) {
        int w = 200, h = 50, pad = 10;
        int px = (width - w) / 2, py = (height - h) / 2;
        drawPopupBox(guiGraphics, px, py, w, h);

        String msg1 = "Not enough energy";
        String msg2 = "to place the structure!";
        int msg1Width = font.width(msg1);
        int mx = px + (w - msg1Width) / 2;
        int my = py + (h - font.lineHeight) / 2;
        guiGraphics.drawString(font, msg1, mx, my, 0xFFFFFF, false);
        guiGraphics.drawString(font, msg2, mx, my + 10, 0xFFFFFF, false);
    }

    private void renderPlacementDelayPopup(GuiGraphics guiGraphics, int ticks) {
        int sec = ticks / 20;
        float frac = (ticks % 20) / 20f;
        String timeText = String.format("Time remaining: %.1fs", sec + frac);

        int w = 200, h = 50, pad = 10;
        int px = (width - w) / 2, py = (height - h) / 2;
        drawPopupBox(guiGraphics, px, py, w, h);

        String title = "Placing Structure...";
        int titleW = font.width(title);
        int tx = px + (w - titleW) / 2;
        int ty = py + pad;
        guiGraphics.drawString(font, title, tx, ty, 0xFFFFFF, false);

        int timeW = font.width(timeText);
        guiGraphics.drawString(font, timeText, px + (w - timeW) / 2, ty + 20, 0xFFFFFF, false);
    }

    private void drawPopupBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xAA000000);
        // top
        g.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        // bottom
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        // left
        g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        // right
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
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
