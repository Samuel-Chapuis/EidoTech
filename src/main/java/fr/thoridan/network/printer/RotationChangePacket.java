package fr.thoridan.network.printer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotationChangePacket {
    private final BlockPos blockEntityPos;
    private final Rotation rotation;

    public RotationChangePacket(BlockPos blockEntityPos, Rotation rotation) {
        this.blockEntityPos = blockEntityPos;
        this.rotation = rotation;
    }

    public RotationChangePacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        int rotationOrdinal = buf.readInt();
        this.rotation = Rotation.values()[rotationOrdinal];
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
                if (blockEntity instanceof PrinterBlockEntity printerBlockEntity) {
                    // Update the block entity's stored rotation
                    printerBlockEntity.setRotation(rotation);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
