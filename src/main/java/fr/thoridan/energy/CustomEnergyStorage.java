package fr.thoridan.energy;

import net.minecraftforge.energy.EnergyStorage;

public class CustomEnergyStorage extends EnergyStorage{
        private Runnable onChange;

        public CustomEnergyStorage(int capacity, Runnable onChange) {
            super(capacity);
            this.onChange = onChange;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0 && onChange != null) {
                onChange.run();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (!simulate && extracted > 0 && onChange != null) {
                onChange.run();
            }
            return extracted;
        }

        public void setEnergy(int energy) {
            this.energy = energy;
            if (onChange != null) {
                onChange.run();
            }
        }
}

