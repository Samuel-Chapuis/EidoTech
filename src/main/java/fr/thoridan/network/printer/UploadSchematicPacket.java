package fr.thoridan.network.printer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UploadSchematicPacket {
    // For example:
    private final String schematicName;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkData;

    public UploadSchematicPacket(String schematicName, int chunkIndex, int totalChunks, byte[] chunkData) {
        this.schematicName = schematicName;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
    }

    public UploadSchematicPacket(FriendlyByteBuf buf) {
        schematicName = buf.readUtf(32767);
        chunkIndex = buf.readInt();
        totalChunks = buf.readInt();
        int dataLength = buf.readInt();
        chunkData = new byte[dataLength];
        buf.readBytes(chunkData);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(schematicName);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(chunkData.length);
        buf.writeBytes(chunkData);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // SERVER-SIDE: Accumulate or store the chunk.
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SchematicManager.storeChunk(player.getUUID(), schematicName, chunkIndex, totalChunks, chunkData);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
