package fr.thoridan.network;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PlaceStructurePacket {
    private final BlockPos blockEntityPos;
    private final BlockPos targetPos;
    private final Rotation rotation;

    public PlaceStructurePacket(BlockPos blockEntityPos, int x, int y, int z, Rotation rotation) {
        this.blockEntityPos = blockEntityPos;
        this.targetPos = new BlockPos(x, y, z);
        this.rotation = rotation;
    }

    public PlaceStructurePacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        this.targetPos = buf.readBlockPos();
        this.rotation = buf.readEnum(Rotation.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
        buf.writeBlockPos(targetPos);
        buf.writeEnum(rotation);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                var level = player.level();
                var blockEntity = level.getBlockEntity(blockEntityPos);
                if (blockEntity instanceof PrinterBlockEntity printerBlockEntity) {
                    printerBlockEntity.placeStructureAt(targetPos, rotation);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}