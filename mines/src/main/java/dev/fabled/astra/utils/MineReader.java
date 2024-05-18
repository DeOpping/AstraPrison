package dev.fabled.astra.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MineReader {
    public static MineData readMineData(String filePath, String mineName) {
        File file = new File(filePath);
        if (!file.exists()) return null;

        try (FileReader reader = new FileReader(file)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            if (jsonObject.has("mines")) {
                JsonArray minesArray = jsonObject.getAsJsonArray("mines");
                for (int i = 0; i < minesArray.size(); i++) {
                    JsonObject mine = minesArray.get(i).getAsJsonObject();
                    if (mine.has("name") && mine.get("name").getAsString().equals(mineName)) {
                        JsonObject pos1 = mine.getAsJsonObject("pos1");
                        JsonObject pos2 = mine.getAsJsonObject("pos2");

                        int startX = pos1.get("startX").getAsInt();
                        int startY = pos1.get("startY").getAsInt();
                        int startZ = pos1.get("startZ").getAsInt();

                        int endX = pos2.get("endX").getAsInt();
                        int endY = pos2.get("endY").getAsInt();
                        int endZ = pos2.get("endZ").getAsInt();

                        String materialString = mine.get("material").getAsString();
                        Material material = parseMaterial(materialString);
                        if (material != null) {
                            return new MineData(startX, startY, startZ, endX, endY, endZ, material);
                        } else {
                            System.err.println("Invalid material name: " + materialString);
                            return null;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Material parseMaterial(String materialString) {
        try {
            return Material.valueOf(materialString);
        } catch (IllegalArgumentException e) {
            // Material not found
            return null;
        }
}
}
