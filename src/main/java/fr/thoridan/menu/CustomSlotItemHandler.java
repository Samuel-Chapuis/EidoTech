package fr.thoridan.menu;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class CustomSlotItemHandler extends SlotItemHandler {

    public CustomSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public int getMaxStackSize() {
        // Return the slot limit from the item handler
        return getItemHandler().getSlotLimit(getSlotIndex());
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        // Return the slot limit, ignoring the item's own max stack size
        return getMaxStackSize();
    }
}
