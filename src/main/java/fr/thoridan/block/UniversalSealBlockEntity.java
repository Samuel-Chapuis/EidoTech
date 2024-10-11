package fr.thoridan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class UniversalSealBlockEntity extends BlockEntity {

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new ItemStackHandler(64)); // 64 items/tick
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> new FluidTank(30000)); // 30k mB/tick = 30 buckets/tick
    private final LazyOptional<IEnergyStorage> energyStorage = LazyOptional.of(() -> new EnergyStorage(500000)); // 500K Rf/tick

    public UniversalSealBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIVERSAL_SEAL_BLOCK_ENTITY.get(), pos, state);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandler.cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return energyStorage.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
        fluidHandler.invalidate();
        energyStorage.invalidate();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, UniversalSealBlockEntity blockEntity) {
        // Logic de transfert d'objets, liquides ou énergie
        if (!level.isClientSide) {
            // Ajouter ici la logique de transfert d'items, fluides ou énergie
        }
    }
}

