package fr.thoridan.network.printer;

import fr.thoridan.Techutilities;
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
            Techutilities.broadcastServerMessage("Rejected upload: size limit exceeded for " + name, false);
            // Optionally remove partial upload data
            playerMap.remove(name);
            return;
        }

        //TODO : Add more security

        // Write the chunk
        try {
            outputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
            // Optionally remove partial upload data
            playerMap.remove(name);
            return;
        }

        // If index+1 == total, we received the final chunk
        if (index + 1 == total) {
            // We have all bytes
            byte[] fullBytes = outputStream.toByteArray();

            // Write them to a server file so the usual loadSchematicData can find it
            File schematicsFolder = new File(FMLPaths.GAMEDIR.get().toFile(), "schematics");
            schematicsFolder.mkdirs(); // ensure the folder exists
            File serverFile = new File(schematicsFolder, name);

            try (FileOutputStream fos = new FileOutputStream(serverFile)) {
                fos.write(fullBytes);
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Now your server has "schematics/<name>" with the full content.
            // So next time loadSchematicData(...) is called, it will succeed.

            // (Optional) parse it in memory as well
            try (ByteArrayInputStream bais = new ByteArrayInputStream(fullBytes)) {
                CompoundTag nbt = NbtIo.readCompressed(bais);
                // Use or store 'nbt' if you want
            } catch (IOException e) {
                e.printStackTrace();
            }

            playerMap.remove(name);
        }
    }
}

