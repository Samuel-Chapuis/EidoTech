package fr.thoridan.network.printer;

import fr.thoridan.client.printer.ui.PrinterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sent from server -> client to inform the client which items are missing.
 */
public class MissingPermissionPacket {
    private final ChunkPos chunk_denied;

    public MissingPermissionPacket(ChunkPos chunk) {
        this.chunk_denied = chunk;
    }

    public MissingPermissionPacket(FriendlyByteBuf buf) {
        this.chunk_denied = new ChunkPos(buf.readInt(), buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(chunk_denied.x);
        buf.writeInt(chunk_denied.z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof PrinterScreen screen) {
                screen.setMissingPermission(chunk_denied);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
