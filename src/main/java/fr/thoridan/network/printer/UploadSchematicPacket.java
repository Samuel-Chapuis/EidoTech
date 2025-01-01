package fr.thoridan.network.printer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class UploadSchematicPacket {
    private final String schematicName;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkData;
    private static final int MAX_SCHEMATIC_SIZE = 1000000; // Limit: e.g. 1 MB per schematic

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
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1) Check extension
                //    (We keep it simple: must end with .nbt OR .schematic)
                String lowerCaseName = schematicName.toLowerCase();
                if (!(lowerCaseName.endsWith(".nbt") || lowerCaseName.endsWith(".schematic"))) {
                    // Optionally, send a chat message or log it
                    System.out.println("Rejected upload: invalid extension for " + schematicName);
                    return; // Stop here
                }

                // 2) Call SchematicManager, which does the main chunk handling
                SchematicManager.storeChunk(
                        player.getUUID(),
                        schematicName,
                        chunkIndex,
                        totalChunks,
                        chunkData,
                        MAX_SCHEMATIC_SIZE
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
