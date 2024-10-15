package fr.thoridan.menu;

import fr.thoridan.block.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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

        // Add slots or other initialization if needed
    }

    @Override
    public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, pos), player, ModBlocks.PRINTER.get());
    }

    public PrinterBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
