package fr.thoridan.network.printer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SchematicManager {
    private static final Map<UUID, Map<String, ByteArrayOutputStream>> UPLOADS = new HashMap<>();

    public static void storeChunk(UUID playerUUID, String name, int index, int total, byte[] data) {
        var playerMap = UPLOADS.computeIfAbsent(playerUUID, k -> new HashMap<>());
        var outputStream = playerMap.computeIfAbsent(name, k -> new ByteArrayOutputStream());

        try {
            // In a real system, you might store index-chunks in a sorted structure.
            // But if the client always sends them in correct order, you can just .write() in sequence:
            outputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
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

