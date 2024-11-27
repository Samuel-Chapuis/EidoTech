package fr.thoridan.network.printer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlacementDelayUpdatePacket {
    private final BlockPos pos;
    private final int placementDelayTicks;

    public PlacementDelayUpdatePacket(BlockPos pos, int placementDelayTicks) {
        this.pos = pos;
        this.placementDelayTicks = placementDelayTicks;
    }

    public PlacementDelayUpdatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.placementDelayTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(placementDelayTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Handle packet on client side
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PrinterBlockEntity printerBE) {
                    printerBE.setClientPlacementDelayTicks(placementDelayTicks);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}