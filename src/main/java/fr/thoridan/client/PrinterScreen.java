package fr.thoridan.client;
import fr.thoridan.Techutilities;
import fr.thoridan.client.widget.TextButton;
import fr.thoridan.menu.PrinterMenu;
import fr.thoridan.network.ModNetworking;
import fr.thoridan.network.PlaceStructurePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Rotations;
import net.minecraft.world.level.block.Rotation;


public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Techutilities.MODID, "textures/gui/printer_gui.png");
    private List<String> schematics;
    private int selectedIndex = -1;
    private EditBox posXField;
    private EditBox posYField;
    private EditBox posZField;
    private EditBox rotationField;


    public PrinterScreen(PrinterMenu menu, Inventory inv, Component titleIn) {
        super(menu, inv, titleIn);
//        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
//        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
//        this.imageWidth = (int) (screenWidth * 0.8);
//        this.imageHeight = (int) (screenHeight * 0.8);
//        // Recalculate leftPos and topPos based on new dimensions
//        this.leftPos = (screenWidth - this.imageWidth) / 2;
//        this.topPos = (screenHeight - this.imageHeight) / 2;
        this.imageWidth = 256;
        this.imageHeight = 166;
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

        int startY = topPos + 20;
        int buttonHeight = 10; // Reduced height since we're only rendering text
        int x = leftPos + 10;  // Adjust padding as needed

        for (int i = 0; i < schematics.size(); i++) {
            final int index = i;
            String name = schematics.get(i);
            Component text = Component.literal(name);

            // Calculate text width
            int textWidth = this.font.width(text);

            this.addRenderableWidget(new TextButton(x, startY + i * (buttonHeight + 2), textWidth, buttonHeight, text, button -> {
                selectedIndex = index;
                // Send packet to the server if needed
            }));

            // Add input fields and place button
            int inputFieldWidth = 50;
            int inputFieldHeight = 20;
            int inputsStartY = topPos + imageHeight - 80;

            // X Position
            posXField = new EditBox(this.font, leftPos + 10, inputsStartY, inputFieldWidth, inputFieldHeight, Component.literal("X"));
            posXField.setValue(String.valueOf(this.minecraft.player.getBlockX()));
            this.addRenderableWidget(posXField);

            // Y Position
            posYField = new EditBox(this.font, leftPos + 70, inputsStartY, inputFieldWidth, inputFieldHeight, Component.literal("Y"));
            posYField.setValue(String.valueOf(this.minecraft.player.getBlockY()));
            this.addRenderableWidget(posYField);

            // Z Position
            posZField = new EditBox(this.font, leftPos + 130, inputsStartY, inputFieldWidth, inputFieldHeight, Component.literal("Z"));
            posZField.setValue(String.valueOf(this.minecraft.player.getBlockZ()));
            this.addRenderableWidget(posZField);

            // Rotation input field
            rotationField = new EditBox(this.font, leftPos + 190, inputsStartY, inputFieldWidth, inputFieldHeight, Component.literal("Rotation"));
            rotationField.setValue("0");
            this.addRenderableWidget(rotationField);

            // Place Structure Button
            this.addRenderableWidget(
                    Button.builder(Component.literal("Place Structure"), button -> {
                                // Handle button click
                                sendPlaceStructurePacket();
                            })
                            .bounds(leftPos + 10, inputsStartY + 30, 150, 20)
                            .build()
            );
        }
    }

    private void sendPlaceStructurePacket() {
        try {
            int x = Integer.parseInt(posXField.getValue());
            int y = Integer.parseInt(posYField.getValue());
            int z = Integer.parseInt(posZField.getValue());
            int rotationDegrees = Integer.parseInt(rotationField.getValue());

            // Convert degrees to Minecraft Rotation enum
            Rotation rotation = switch (rotationDegrees % 360) {
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            // Send packet to server
            ModNetworking.INSTANCE.sendToServer(new PlaceStructurePacket(menu.getBlockEntity().getBlockPos(), x, y, z, rotation));
        } catch (NumberFormatException e) {
            System.out.println("Invalid position or rotation");
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
//        // Draw custom title
//        int titleX = (this.imageWidth - this.font.width("NBT Printer")) / 2;
//        guiGraphics.drawString(this.font, "NBT Printer", titleX, 10, 0xFFFFFF, false);
//
//        // Render labels for input fields
//        guiGraphics.drawString(this.font, "X:", posXField.getX() - this.leftPos - 15, posXField.getY() - this.topPos + 6, 0xFFFFFF, false);
//        guiGraphics.drawString(this.font, "Y:", posYField.getX() - this.leftPos - 15, posYField.getY() - this.topPos + 6, 0xFFFFFF, false);
//        guiGraphics.drawString(this.font, "Z:", posZField.getX() - this.leftPos - 15, posZField.getY() - this.topPos + 6, 0xFFFFFF, false);
//        guiGraphics.drawString(this.font, "Rot:", rotationField.getX() - this.leftPos - 25, rotationField.getY() - this.topPos + 6, 0xFFFFFF, false);
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

}
