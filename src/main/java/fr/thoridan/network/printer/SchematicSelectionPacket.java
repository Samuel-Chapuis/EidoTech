package fr.thoridan.network.printer;

import fr.thoridan.block.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client -> server to select which schematic name
 * should be stored in the PrinterBlockEntity.
 */
public class SchematicSelectionPacket {
    private final BlockPos blockEntityPos;
    private final String schematicName;

    public SchematicSelectionPacket(BlockPos blockEntityPos, String schematicName) {
        this.blockEntityPos = blockEntityPos;
        this.schematicName = schematicName;
    }

    public SchematicSelectionPacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        this.schematicName = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
        buf.writeUtf(schematicName);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                var level = player.level();
                var blockEntity = level.getBlockEntity(blockEntityPos);
                if (blockEntity instanceof PrinterBlockEntity printer) {
                    printer.setSchematicName(schematicName);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
