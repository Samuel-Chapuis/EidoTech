package fr.thoridan.network.printer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client -> server to update the printer's rotation setting.
 */
public class RotationChangePacket {
    private final BlockPos blockEntityPos;
    private final Rotation rotation;

    public RotationChangePacket(BlockPos blockEntityPos, Rotation rotation) {
        this.blockEntityPos = blockEntityPos;
        this.rotation = rotation;
    }

    public RotationChangePacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        this.rotation = Rotation.values()[buf.readInt()];
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
        buf.writeInt(rotation.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                var level = player.level();
                var blockEntity = level.getBlockEntity(blockEntityPos);
                if (blockEntity instanceof PrinterBlockEntity printer) {
                    printer.setRotation(rotation);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
