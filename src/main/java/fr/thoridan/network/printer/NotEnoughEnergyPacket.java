package fr.thoridan.network.printer;

import fr.thoridan.client.printer.ui.PrinterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class NotEnoughEnergyPacket {

    public NotEnoughEnergyPacket() {
        // Empty constructor, no data to serialize
    }

    public NotEnoughEnergyPacket(FriendlyByteBuf buf) {
        // Empty, no data to read
    }

    public void toBytes(FriendlyByteBuf buf) {
        // Empty, no data to write
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // This runs on the client side
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof PrinterScreen) {
                PrinterScreen screen = (PrinterScreen) mc.screen;
                screen.displayNotEnoughEnergyPopup();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}