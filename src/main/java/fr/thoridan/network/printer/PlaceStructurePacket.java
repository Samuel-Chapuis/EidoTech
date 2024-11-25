package fr.thoridan.network.printer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PlaceStructurePacket {
    private final BlockPos blockEntityPos;
    private final BlockPos targetPos;
    private final Rotation rotation;
    private final String schematicName; // Add this field

    public PlaceStructurePacket(BlockPos blockEntityPos, int x, int y, int z, Rotation rotation, String schematicName) {
        this.blockEntityPos = blockEntityPos;
        this.targetPos = new BlockPos(x, y, z);
        this.rotation = rotation;
        this.schematicName = schematicName;
    }

    public PlaceStructurePacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        this.targetPos = buf.readBlockPos();
        this.rotation = buf.readEnum(Rotation.class);
        this.schematicName = buf.readUtf(32767); // Read the schematic name
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
        buf.writeBlockPos(targetPos);
        buf.writeEnum(rotation);
        buf.writeUtf(schematicName); // Write the schematic name
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                var level = player.level();
                var blockEntity = level.getBlockEntity(blockEntityPos);
                if (blockEntity instanceof PrinterBlockEntity printerBlockEntity) {
                    // Place the structure and pass the player
                    printerBlockEntity.placeStructureAt(targetPos, rotation, schematicName, player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
