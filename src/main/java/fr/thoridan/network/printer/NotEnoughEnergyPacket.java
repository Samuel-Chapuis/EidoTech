package fr.thoridan.network.printer;

import fr.thoridan.client.printer.ui.PrinterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server -> client to notify that there's not enough energy to place the structure.
 */
public class NotEnoughEnergyPacket {
    public NotEnoughEnergyPacket() {}

    public NotEnoughEnergyPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof PrinterScreen screen) {
                screen.displayNotEnoughEnergyPopup();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
