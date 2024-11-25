package fr.thoridan.menu;

import fr.thoridan.block.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
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

        // Printer inventory slots (7 rows x 12 columns = 84 slots)
        for (int row = 0; row < 7; ++row) {
            for (int col = 0; col < 12; ++col) {
                int index = col + row * 12;
                int x = startX + col * slotSizePlus2;
                int y = startY + row * slotSizePlus2;
                this.addSlot(new CustomSlotItemHandler(handler, index, x, y));
            }
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int startX = 8;
        int slotSizePlus2 = 18;

        // Increase the space between machine inventory and player inventory
        int additionalSpace = 34; // Adjust this value as needed
        int startY = 18 + (7 * slotSizePlus2) + additionalSpace;

        // Player inventory slots (3 rows x 9 columns)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int index = col + row * 9 + 9;
                int x = startX + col * slotSizePlus2;
                int y = startY + row * slotSizePlus2;
                this.addSlot(new Slot(playerInventory, index, x, y));
            }
        }

        // Hotbar slots
        int hotbarY = startY + (3 * slotSizePlus2) + 4;
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
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < 84) { // From printer inventory to player inventory
                if (!this.moveItemStackTo(stackInSlot, 84, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else { // From player inventory to printer inventory
                if (!this.moveItemStackTo(stackInSlot, 0, 84, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.getCount() == 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        boolean flag = false;
        int i = startIndex;

        if (reverseDirection) {
            i = endIndex - 1;
        }

        while (stack.getCount() > 0 && (reverseDirection ? i >= startIndex : i < endIndex)) {
            Slot slot = this.slots.get(i);
            ItemStack existingStack = slot.getItem();

            if (slot.mayPlace(stack) && ItemStack.isSameItemSameTags(stack, existingStack)) {
                int maxStackSize = slot.getMaxStackSize(stack);

                int combinedCount = existingStack.getCount() + stack.getCount();

                if (combinedCount <= maxStackSize) {
                    stack.setCount(0);
                    existingStack.setCount(combinedCount);
                    slot.setChanged();
                    flag = true;
                } else if (existingStack.getCount() < maxStackSize) {
                    int remaining = maxStackSize - existingStack.getCount();
                    stack.shrink(remaining);
                    existingStack.setCount(maxStackSize);
                    slot.setChanged();
                    flag = true;
                }
            }

            i += reverseDirection ? -1 : 1;
        }

        if (stack.getCount() > 0) {
            i = reverseDirection ? endIndex - 1 : startIndex;
            while (reverseDirection ? i >= startIndex : i < endIndex) {
                Slot slot = this.slots.get(i);
                ItemStack existingStack = slot.getItem();

                if (slot.mayPlace(stack) && existingStack.isEmpty()) {
                    int maxStackSize = slot.getMaxStackSize(stack);
                    int count = Math.min(stack.getCount(), maxStackSize);

                    slot.set(stack.split(count));
                    slot.setChanged();
                    flag = true;

                    if (stack.getCount() == 0) {
                        break;
                    }
                }

                i += reverseDirection ? -1 : 1;
            }
        }

        return flag;
    }


}
