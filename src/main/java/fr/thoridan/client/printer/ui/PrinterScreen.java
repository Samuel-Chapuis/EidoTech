package fr.thoridan.client.printer.ui;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.thoridan.Techutilities;
import fr.thoridan.block.PrinterBlockEntity;
import fr.thoridan.client.printer.widget.TextButton;
import fr.thoridan.menu.PrinterMenu;
import fr.thoridan.network.*;
import fr.thoridan.network.printer.PlaceStructurePacket;
import fr.thoridan.network.printer.PositionUpdatePacket;
import fr.thoridan.network.printer.RotationChangePacket;
import fr.thoridan.network.printer.SchematicSelectionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.client.gui.components.CycleButton;


public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Techutilities.MODID, "textures/gui/printer_gui.png");
    private static final ResourceLocation SECOND_TEXTURE = new ResourceLocation(Techutilities.MODID, "textures/gui/second_image.png");
    private Map<Item, Integer> missingItems = Collections.emptyMap();
    private List<String> schematics;
    private List<TextButton> schematicButtons = new ArrayList<>();
    private CycleButton<Integer> rotationButton;
    private int selectedIndex = -1;
    private String selectedSchematicName = null;

    private EditBox posXField;
    private EditBox posYField;
    private EditBox posZField;


    public PrinterScreen(PrinterMenu menu, Inventory inv, Component titleIn) {
        super(menu, inv, titleIn);
        this.imageWidth = 8 + (12 * 18) + 8; // Adjusted width for 12 columns
        this.imageHeight = 18 + (7 * 18) + 4 + (3 * 18) + 4 + 18 + 4 + 28; // Adjusted height for 7 rows and player inventory
        this.schematics = new ArrayList<>();
        loadSchematics();
    }

    private void loadSchematics() {
        File schematicsFolder = new File(Minecraft.getInstance().gameDirectory, "schematics");
        if (schematicsFolder.exists() && schematicsFolder.isDirectory()) {
            File[] files = schematicsFolder.listFiles((dir, name) -> name.endsWith(".schematic") || name.endsWith(".nbt"));
            if (files != null) {
                for (File file : files) {
                    schematics.add(file.getName());
                }
            }
        }
    }


    @Override
    protected void init() {
        super.init();

        // Recalculate leftPos and topPos
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int inputFieldWidth = 50;
        int inputFieldHeight = 20;
        int inputsStartY = topPos + imageHeight - 80;

        // Initialize input fields first
        // X Position
        posXField = new EditBox(this.font, leftPos - 3*( inputFieldWidth + 10), topPos + inputFieldHeight + 15, inputFieldWidth, inputFieldHeight, Component.literal("X"));
        posXField.setValue(String.valueOf(this.minecraft.player.getBlockX()));
        this.addRenderableWidget(posXField);

        // Y Position
        posYField = new EditBox(this.font, leftPos - 2*( inputFieldWidth + 10), topPos + inputFieldHeight + 15, inputFieldWidth, inputFieldHeight, Component.literal("Y"));
        posYField.setValue(String.valueOf(this.minecraft.player.getBlockY()));
        this.addRenderableWidget(posYField);

        // Z Position
        posZField = new EditBox(this.font, leftPos - 1*( inputFieldWidth + 10), topPos + inputFieldHeight + 15, inputFieldWidth, inputFieldHeight, Component.literal("Z"));
        posZField.setValue(String.valueOf(this.minecraft.player.getBlockZ()));
        this.addRenderableWidget(posZField);

        // Load stored values from the block entity
        PrinterBlockEntity blockEntity = menu.getBlockEntity();
        if (blockEntity.getStoredTargetPos() != null) {
            posXField.setValue(String.valueOf(blockEntity.getStoredTargetPos().getX()));
            posYField.setValue(String.valueOf(blockEntity.getStoredTargetPos().getY()));
            posZField.setValue(String.valueOf(blockEntity.getStoredTargetPos().getZ()));
        }

        // Load the stored schematic name
        if (blockEntity.getStoredSchematicName() != null) {
            selectedSchematicName = blockEntity.getStoredSchematicName();
            selectedIndex = schematics.indexOf(selectedSchematicName);
        } else {
            selectedSchematicName = null;
            selectedIndex = -1;
        }

        // Initialize rotation values
        int initialRotationDegrees = 0;
        if (blockEntity.getStoredRotation() != null) {
            initialRotationDegrees = getDegreesFromRotation(blockEntity.getStoredRotation());
        }

        // Now create the schematic buttons
        createSchematicButtons();

        // Update the button colors to reflect the stored selection
        updateSchematicButtonColors();

        // Rotation CycleButton
        rotationButton = CycleButton.<Integer>builder(degrees -> Component.literal(degrees + "°"))
                .withValues(0, 90, 180, 270)
                .displayOnlyValue()
                .withInitialValue(initialRotationDegrees)
                .create(leftPos - 9 - inputFieldWidth, topPos + 5, inputFieldWidth, inputFieldHeight, Component.literal("Rotation"), (button, value) -> {
                    // Handle rotation change
                    Rotation rotation = switch (value) {
                        case 90 -> Rotation.CLOCKWISE_90;
                        case 180 -> Rotation.CLOCKWISE_180;
                        case 270 -> Rotation.COUNTERCLOCKWISE_90;
                        default -> Rotation.NONE;
                    };

                    // Send packet to server to update the block entity
                    ModNetworking.INSTANCE.sendToServer(new RotationChangePacket(
                            menu.getBlockEntity().getBlockPos(),
                            rotation
                    ));
                });
        this.addRenderableWidget(rotationButton);

        // Place Structure Button
        this.addRenderableWidget(
                Button.builder(Component.literal("Place Structure"), button -> {
                            // Handle button click
                            sendPlaceStructurePacket();
                        })
                        .bounds(leftPos - 100 - 31 - inputFieldWidth, topPos + 5, 100, 20)
                        .build()
        );
    }

    private void createSchematicButtons() {
        int startY = topPos + 65;
        int buttonHeight = 10; // Adjust as needed
        int x = leftPos - 100 - 31 - 50;  // Adjust padding as needed

        // Clear the list of buttons in case createSchematicButtons() is called multiple times
        schematicButtons.clear();

        for (int i = 0; i < schematics.size(); i++) {
            final int index = i;
            String name = schematics.get(i);
            Component text = Component.literal(name);

            // Calculate text width
            int textWidth = this.font.width(text);

            // Determine text color based on selection
            int textColor = (index == selectedIndex) ? 0xFFFF00 : 0xFFFFFF; // Yellow if selected, white otherwise

            TextButton button = new TextButton(x, startY + i * (buttonHeight + 2), textWidth, buttonHeight, text, btn -> {
                selectedIndex = index;
                selectedSchematicName = schematics.get(index);

                // Update button colors to reflect new selection
                updateSchematicButtonColors();

                // Send packet to server to update the block entity
                ModNetworking.INSTANCE.sendToServer(new SchematicSelectionPacket(
                        menu.getBlockEntity().getBlockPos(),
                        selectedSchematicName
                ));
            }, textColor);

            schematicButtons.add(button);
            this.addRenderableWidget(button);
        }
    }


    private void sendPlaceStructurePacket() {
        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            int rotationDegrees = rotationButton.getValue();

            // Convert degrees to Minecraft Rotation enum
            Rotation rotation = switch (rotationDegrees) {
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            if (selectedSchematicName == null) {
                System.out.println("No schematic selected");
                return;
            }

            // Send packet to server to place the structure and update the block entity
            ModNetworking.INSTANCE.sendToServer(new PlaceStructurePacket(
                    menu.getBlockEntity().getBlockPos(),
                    x, y, z,
                    rotation,
                    selectedSchematicName
            ));

        } catch (NumberFormatException e) {
            System.out.println("Invalid position");
        }
    }




    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        // Draw the main background texture
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight);

        // Now draw the additional background texture
        RenderSystem.setShaderTexture(0, SECOND_TEXTURE);
        // For example, let's position it at (leftPos + 50, topPos + 50) with a width and height of 100 pixels
        guiGraphics.blit(SECOND_TEXTURE, leftPos - 185, topPos, 0, 0, 180, 166);
    }


    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Draw the title (e.g., "Printer")
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);

        // Calculate the Y position for the separator and the "Player Inventory" label
        int separatorY = 18 + (7 * 18) + 4 + 6; // Adjusted for the added space
        int labelY = separatorY + 8; // Position the label below the separator

        // Draw the separator line
        int lineStartX = 8;
        int lineEndX = this.imageWidth - 8;
        guiGraphics.hLine(lineStartX, lineEndX, separatorY, 0xFF404040); // Light gray line

        // Draw the "Player Inventory" label
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, labelY, 4210752, false);
    }


    private void updateSchematicButtonColors() {
        for (int i = 0; i < schematicButtons.size(); i++) {
            TextButton button = schematicButtons.get(i);
            int textColor = (i == selectedIndex) ? 0xFFFF00 : 0xFFFFFF; // Yellow if selected, white otherwise
            button.setTextColor(textColor);
        }
    }


    private int getDegreesFromRotation(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
            default -> 0;
        };
    }

    @Override
    public void removed() {
        super.removed();

        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            BlockPos targetPos = new BlockPos(x, y, z);

            // Send packet to server to update the block entity
            ModNetworking.INSTANCE.sendToServer(new PositionUpdatePacket(
                    menu.getBlockEntity().getBlockPos(),
                    targetPos
            ));

        } catch (NumberFormatException e) {
            System.out.println("Invalid position");
        }
    }


    public void setMissingItems(Map<Item, Integer> missingItems) {
        this.missingItems = missingItems;
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        if (!missingItems.isEmpty()) {
            renderMissingItemsPopup(guiGraphics, mouseX, mouseY);
        }

        // Get the placementDelayTicks from blockEntity
        int placementDelayTicks = menu.getBlockEntity().getClientPlacementDelayTicks();

        if (placementDelayTicks > 0) {
            renderPlacementDelayPopup(guiGraphics, placementDelayTicks);
        }
    }


    private void renderMissingItemsPopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Minimum popup width
        int minPopupWidth = 150;
        int padding = 10; // Padding on each side

        // Measure the width of the title
        int titleWidth = this.font.width("Missing Items:");

        // Measure the width of each missing item line and find the maximum
        int maxTextWidth = titleWidth;
        for (Map.Entry<Item, Integer> entry : missingItems.entrySet()) {
            String itemName = entry.getKey().getDescription().getString();
            int count = entry.getValue();
            String text = count + " x " + itemName;
            int textWidth = this.font.width(text);
            if (textWidth > maxTextWidth) {
                maxTextWidth = textWidth;
            }
        }

        // Calculate the popup width based on the longest text line plus padding
        int popupWidth = Math.max(minPopupWidth, maxTextWidth + padding * 2);

        // Calculate the popup height based on the number of lines
        int lineHeight = 10; // Height of each text line
        int titleHeight = 15; // Height of the title
        int popupHeight = titleHeight + (missingItems.size() * lineHeight) + padding * 2;

        // Center the popup on the screen
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        // Draw background
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xAA000000);

        // Draw border using fill method (1-pixel borders)
        // Top border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFFFFFFFF);
        // Bottom border
        guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFFFFFFFF);
        // Left border
        guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFFFFFFFF);
        // Right border
        guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFFFFFFFF);

        // Draw title
        int textX = popupX + padding;
        int textY = popupY + padding;
        guiGraphics.drawString(this.font, "Missing Items:", textX, textY, 0xFFFFFF, false);

        // Draw each missing item
        textY += titleHeight;
        for (Map.Entry<Item, Integer> entry : missingItems.entrySet()) {
            String itemName = entry.getKey().getDescription().getString();
            int count = entry.getValue();
            String text = count + " x " + itemName;
            guiGraphics.drawString(this.font, text, textX, textY, 0xFFFFFF, false);
            textY += lineHeight;
        }
    }


    private void renderPlacementDelayPopup(GuiGraphics guiGraphics, int placementDelayTicks) {
        // Convert ticks to seconds
        int secondsRemaining = placementDelayTicks / 20;
        float partialSeconds = (placementDelayTicks % 20) / 20.0f;
        String timeText = String.format("Time remaining: %.1fs", secondsRemaining + partialSeconds);

        // Prepare the popup dimensions
        int popupWidth = 200;
        int popupHeight = 50;
        int padding = 10;

        // Center the popup on the screen
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        // Draw background
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xAA000000);

        // Draw border
        // Top border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFFFFFFFF);
        // Bottom border
        guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFFFFFFFF);
        // Left border
        guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFFFFFFFF);
        // Right border
        guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFFFFFFFF);

        // Draw the title
        String title = "Placing Structure...";
        int titleWidth = this.font.width(title);
        int titleX = popupX + (popupWidth - titleWidth) / 2;
        int titleY = popupY + padding;
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFF, false);

        // Draw the remaining time
        int timeWidth = this.font.width(timeText);
        int timeX = popupX + (popupWidth - timeWidth) / 2;
        int timeY = titleY + 20; // Adjust as needed
        guiGraphics.drawString(this.font, timeText, timeX, timeY, 0xFFFFFF, false);
    }






}
