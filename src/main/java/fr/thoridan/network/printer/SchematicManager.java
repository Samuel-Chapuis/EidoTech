package fr.thoridan.network.printer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SchematicManager {
    // Tracks all in-progress uploads by (player -> (schematicName -> partial data))
    private static final Map<UUID, Map<String, ByteArrayOutputStream>> UPLOADS = new HashMap<>();

    /**
     * Store one chunk of uploaded data.
     * @param maxSize  The maximum total byte size of the final schematic.
     */
    public static void storeChunk(UUID playerUUID, String name, int index, int total, byte[] data, int maxSize) {
        // Get or create the ByteArrayOutputStream for this player & schematic
        var playerMap = UPLOADS.computeIfAbsent(playerUUID, k -> new HashMap<>());
        var outputStream = playerMap.computeIfAbsent(name, k -> new ByteArrayOutputStream());

        // Before writing the chunk, check if we’d exceed the limit
        if (outputStream.size() + data.length > maxSize) {
            System.out.println("Rejected upload: size limit exceeded for " + name);
            // Optionally remove partial upload data
            playerMap.remove(name);
            return;
        }

        // Write the chunk
        try {
            outputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
            // Optionally remove partial upload data
            playerMap.remove(name);
            return;
        }

        // If index+1 == total, we have the final chunk
        if (index + 1 == total) {
            byte[] fullBytes = outputStream.toByteArray();
            System.out.println("Upload complete: " + name + " (" + fullBytes.length + " bytes)");
            playerMap.remove(name); // remove from map now

            // Optional: Immediately parse NBT to confirm it’s valid
            try (var bais = new ByteArrayInputStream(fullBytes)) {
                CompoundTag nbt = NbtIo.readCompressed(bais);

                //TODO
                // Do something with 'nbt':
                // Option A: store in memory
                // SchematicDataRegistry.put(name, nbt);
                // Option B: write to a file so loadSchematicData(...) can find it
                // e.g. Files.write(Path.of("schematics", name), fullBytes);

            } catch (IOException e) {
                System.out.println("Upload parse failed for: " + name);
                e.printStackTrace();
            }
        }
    }
}

