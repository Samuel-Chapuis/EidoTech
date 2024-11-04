package fr.thoridan.network;

import fr.thoridan.Techutilities;
import fr.thoridan.network.printer.PlaceStructurePacket;
import fr.thoridan.network.printer.PositionUpdatePacket;
import fr.thoridan.network.printer.RotationChangePacket;
import fr.thoridan.network.printer.SchematicSelectionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Techutilities.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void registerPackets() {
        int id = 0;
        INSTANCE.registerMessage(id++, PlaceStructurePacket.class, PlaceStructurePacket::toBytes, PlaceStructurePacket::new, PlaceStructurePacket::handle);
        INSTANCE.registerMessage(id++, SchematicSelectionPacket.class, SchematicSelectionPacket::toBytes, SchematicSelectionPacket::new, SchematicSelectionPacket::handle);
        INSTANCE.registerMessage(id++, RotationChangePacket.class, RotationChangePacket::toBytes, RotationChangePacket::new, RotationChangePacket::handle);
        INSTANCE.registerMessage(id++, PositionUpdatePacket.class, PositionUpdatePacket::toBytes, PositionUpdatePacket::new, PositionUpdatePacket::handle);
        // Register other packets here
    }
}


