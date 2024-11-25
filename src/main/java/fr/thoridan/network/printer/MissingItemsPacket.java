package fr.thoridan.network.printer;

import fr.thoridan.client.printer.ui.PrinterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MissingItemsPacket {
    private final Map<Item, Integer> missingItems;

    public MissingItemsPacket(Map<Item, Integer> missingItems) {
        this.missingItems = missingItems;
    }

    public MissingItemsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        missingItems = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String itemId = buf.readUtf();
            int count = buf.readInt();
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null) {
                missingItems.put(item, count);
            }
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(missingItems.size());
        for (Map.Entry<Item, Integer> entry : missingItems.entrySet()) {
            buf.writeUtf(ForgeRegistries.ITEMS.getKey(entry.getKey()).toString());
            buf.writeInt(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // On client side
            Minecraft mc = Minecraft.getInstance();
            // Get the open screen and update it with the missing items
            if (mc.screen instanceof PrinterScreen) {
                ((PrinterScreen) mc.screen).setMissingItems(missingItems);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}