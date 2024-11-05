package fr.thoridan.menu;

import fr.thoridan.block.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.ObjectHolder;
import fr.thoridan.menu.ModMenus;
import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PrinterMenu extends AbstractContainerMenu {
    private final PrinterBlockEntity blockEntity;
    private final Level level;
    private final BlockPos pos;


    public PrinterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf data) {
        this(windowId, playerInventory, playerInventory.player.level().getBlockEntity(data.readBlockPos()));
    }

    public PrinterMenu(int windowId, Inventory playerInventory, BlockEntity entity) {
        super(ModMenus.PRINTER_MENU.get(), windowId);
        this.blockEntity = (PrinterBlockEntity) entity;
        this.level = playerInventory.player.level();
        this.pos = blockEntity.getBlockPos();
        // Add slots for Printer inventory (3 rows x 9 columns)
        addPrinterInventorySlots(playerInventory);
        // Add player inventory slots
        addPlayerInventorySlots(playerInventory);

        // Add slots or other initialization if needed
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, pos), player, ModBlocks.PRINTER.get());
    }

    public PrinterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private void addPrinterInventorySlots(Inventory playerInventory) {
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(() -> new IllegalStateException("No item handler capability found"));

        int startX = 8; // Left position of the inventory slots
        int startY = 18; // Top position of the inventory slots
        int slotSizePlus2 = 18;

        // Printer inventory slots (27 slots)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int index = col + row * 9;
                int x = startX + col * slotSizePlus2;
                int y = startY + row * slotSizePlus2;
                this.addSlot(new SlotItemHandler(handler, index, x, y));
            }
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int startX = 8;
        int startY = 86; // Adjust this value based on your GUI layout
        int slotSizePlus2 = 18;

        // Player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int index = col + row * 9 + 9;
                int x = startX + col * slotSizePlus2;
                int y = startY + row * slotSizePlus2;
                this.addSlot(new Slot(playerInventory, index, x, y));
            }
        }

        // Hotbar slots
        int hotbarY = startY + 58; // Adjust this value based on your GUI layout
        for (int col = 0; col < 9; ++col) {
            int x = startX + col * slotSizePlus2;
            this.addSlot(new Slot(playerInventory, col, x, hotbarY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();

            if (index < 27) { // Shift-clicked in printer inventory
                if (!this.moveItemStackTo(stack, 27, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else { // Shift-clicked in player inventory
                if (!this.moveItemStackTo(stack, 0, 27, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

}
