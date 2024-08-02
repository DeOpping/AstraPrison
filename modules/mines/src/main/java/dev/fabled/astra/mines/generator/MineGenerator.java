package dev.fabled.astra.mines.generator;

import dev.fabled.astra.Astra;
import dev.fabled.astra.utils.MineData;
import dev.fabled.astra.utils.MineReader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.*;

import static dev.fabled.astra.utils.MineWriter.FILE;

public class MineGenerator {

    public static Map<UUID, Collection<BlockState>> playerBlockChangesMap = new HashMap<>();
    public static Map<Location, String> fakeBlockMap = new HashMap<Location, String>();

    public static Collection<BlockState> getBlocks(UUID playerUUID, String mineName) {
        ArrayList<BlockState> blockChanges = new ArrayList<>();

        World world = Bukkit.getWorld("world");
        String filePath = FILE;

        MineData mineData = MineReader.readMineData(filePath, mineName);

        if (mineData != null) {
            int startX = mineData.getStartX();
            int startY = mineData.getStartY();
            int startZ = mineData.getStartZ();
            int endX = mineData.getEndX();
            int endY = mineData.getEndY();
            int endZ = mineData.getEndZ();

            if (startX > endX) {
                int temp = startX;
                startX = endX;
                endX = temp;
            }
            if (startY > endY) {
                int temp = startY;
                startY = endY;
                endY = temp;
            }
            if (startZ > endZ) {
                int temp = startZ;
                startZ = endZ;
                endZ = temp;
            }

            List<Material> materials;
            if (mineData.luckyblocks()) {
                materials = List.of(
                        new Material(mineData.getMaterial().name(), 40.0),
                        new Material(mineData.getMaterial2().name(), 30.0),
                        new Material(mineData.getMaterial3().name(), 29.0),
                        new Material(MineData.luckyblockMaterial().name(), 1.0)
                );
            } else {
                materials = List.of(
                        new Material(mineData.getMaterial().name(), 40.0),
                        new Material(mineData.getMaterial2().name(), 30.0),
                        new Material(mineData.getMaterial3().name(), 30.0)
                );
            }

            playerBlockChangesMap.remove(playerUUID);

            int gap = mineData.airgap() ? 1 : 0;
            int adjustedStartX = startX + gap;
            int adjustedStartY = startY;
            int adjustedStartZ = startZ + gap;
            int adjustedEndX = endX - gap;
            int adjustedEndY = endY;
            int adjustedEndZ = endZ - gap;

            for (int x = adjustedStartX; x <= adjustedEndX; ++x) {
                for (int y = adjustedStartY; y <= adjustedEndY; ++y) {
                    for (int z = adjustedStartZ; z <= adjustedEndZ; ++z) {
                        String selectedMaterial = selectMaterialBasedOnPercentage(materials);
                        if (selectedMaterial == null) {
                            Astra.getPlugin().getLogger().severe("Wrong material: " + selectedMaterial);
                            continue;
                        }
                        Location blockLocation = new Location(world, x, y, z);
                        Block block = blockLocation.getBlock();
                        BlockState blockState = block.getState();
                        String minecraftMaterial = convertToMinecraftMaterial(selectedMaterial);
                        BlockData blockData = Bukkit.createBlockData(minecraftMaterial);
                        blockState.setBlockData(blockData);
                        blockState.setType(org.bukkit.Material.valueOf(selectedMaterial));

                        savePlayerUUIDForBlock(blockState, playerUUID);

                        blockState.setMetadata("material", new FixedMetadataValue(Astra.getPlugin(), selectedMaterial));
                        blockState.setMetadata("mineName", new FixedMetadataValue(Astra.getPlugin(), mineName));
                        blockState.setMetadata("amount", new FixedMetadataValue(Astra.getPlugin(), 1.0));

                        fakeBlockMap.put(blockLocation, selectedMaterial);
                        blockChanges.add(blockState);
                    }
                }
            }

            playerBlockChangesMap.put(playerUUID, blockChanges);
        }

        return blockChanges;
    }

    private static String selectMaterialBasedOnPercentage(List<Material> materials) {
        Random random = new Random();
        double rand = random.nextDouble() * 100;
        double cumulativePercentage = 0.0;
        for (Material material : materials) {
            cumulativePercentage += material.getPercentage();
            if (rand <= cumulativePercentage) {
                return material.getName();
            }
        }
        return null;
    }

    public static Map<Location, String> getFakeBlocksForPlayer(UUID playerUUID) {
        Map<Location, String> playerFakeBlocks = new HashMap<>();

        for (Map.Entry<Location, String> entry : fakeBlockMap.entrySet()) {
            Location location = entry.getKey();
            String material = entry.getValue();
            Block block = location.getBlock();
            BlockState blockState = block.getState();

            if (blockState.hasMetadata("playerUUID")) {
                List<MetadataValue> metadataValues = blockState.getMetadata("playerUUID");
                for (MetadataValue value : metadataValues) {
                    if (value.value() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<BlockState, List<UUID>> map = (Map<BlockState, List<UUID>>) value.value();
                        for (Map.Entry<BlockState, List<UUID>> blockEntry : map.entrySet()) {
                            if (areBlockStatesEqual(blockEntry.getKey(), playerUUID)) {
                                playerFakeBlocks.put(location, material);
                            }
                        }
                    }
                }
            }
        }

        return playerFakeBlocks;
    }


    public static void removeBlockFromMap(BlockState blockState, UUID playerUUID) {
        Collection<BlockState> blockChanges = playerBlockChangesMap.get(playerUUID);
        if (blockChanges != null) {
            blockChanges.remove(blockState);
        }
    }

    private static void savePlayerUUIDForBlock(BlockState blockState, UUID playerUUID) {
        Map<BlockState, List<UUID>> blockStatePlayerUUIDsMap;
        if (blockState.hasMetadata("playerUUID")) {
            try {
                List<MetadataValue> metadataValues = blockState.getMetadata("playerUUID");
                if (!metadataValues.isEmpty()) {
                    MetadataValue metadataValue = metadataValues.get(0);
                    if (metadataValue.value() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<BlockState, List<UUID>> existingMap = (Map<BlockState, List<UUID>>) metadataValue.value();
                        blockStatePlayerUUIDsMap = new HashMap<>(existingMap);
                    } else {
                        blockStatePlayerUUIDsMap = new HashMap<>();
                    }
                } else {
                    blockStatePlayerUUIDsMap = new HashMap<>();
                }
            } catch (ClassCastException e) {
                e.printStackTrace();
                blockStatePlayerUUIDsMap = new HashMap<>();
            }
        } else {
            blockStatePlayerUUIDsMap = new HashMap<>();
        }

        List<UUID> playerUUIDs = blockStatePlayerUUIDsMap.computeIfAbsent(blockState, k -> new ArrayList<>());
        playerUUIDs.add(playerUUID);
        blockState.setMetadata("playerUUID", new FixedMetadataValue(Astra.getPlugin(), blockStatePlayerUUIDsMap));
    }



    public static List<UUID> getPlayerUUIDsForBlock(Block block, UUID playerUUID) {
        List<UUID> playerUUIDs = new ArrayList<>();
        BlockState blockState = block.getState();

        if (blockState.hasMetadata("playerUUID")) {
            List<MetadataValue> metadataValues = blockState.getMetadata("playerUUID");
            for (MetadataValue value : metadataValues) {
                if (value.value() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<BlockState, List<UUID>> map = (Map<BlockState, List<UUID>>) value.value();
                    for (Map.Entry<BlockState, List<UUID>> entry : map.entrySet()) {
                        if (areBlockStatesEqual(entry.getKey(), playerUUID)) {
                            playerUUIDs.addAll(entry.getValue());
                        }
                    }
                }
            }
        }

        return playerUUIDs;
    }

    private static class Material {
        private final String name;
        private final double percentage;

        Material(String name, double percentage) {
            this.name = name;
            this.percentage = percentage;
        }

        String getName() {
            return name;
        }

        double getPercentage() {
            return percentage;
        }
    }





    private static boolean areBlockStatesEqual(BlockState blockState1, UUID playerUUID) {
        boolean hasPlayerUUIDMetadata = blockState1.hasMetadata("playerUUID");
        if (hasPlayerUUIDMetadata) {
            List<MetadataValue> metadataValues = blockState1.getMetadata("playerUUID");
            for (MetadataValue value : metadataValues) {
                if (value.value() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<BlockState, List<UUID>> map = (Map<BlockState, List<UUID>>) value.value();
                    for (Map.Entry<BlockState, List<UUID>> entry : map.entrySet()) {
                        if (entry.getValue().contains(playerUUID)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static int getTotalBlocks(String mineName) {
        String filePath = FILE;
        MineData mineData = MineReader.readMineData(filePath, mineName);

        if (mineData != null) {
            int startX = mineData.getStartX();
            int startY = mineData.getStartY();
            int startZ = mineData.getStartZ();
            int endX = mineData.getEndX();
            int endY = mineData.getEndY();
            int endZ = mineData.getEndZ();

            if (startX > endX) {
                int temp = startX;
                startX = endX;
                endX = temp;
            }
            if (startY > endY) {
                int temp = startY;
                startY = endY;
                endY = temp;
            }
            if (startZ > endZ) {
                int temp = startZ;
                startZ = endZ;
                endZ = temp;
            }
            int blockCount = (endX - startX + 1) * (endY - startY + 1) * (endZ - startZ + 1);
            if (mineData.airgap()) {
                int gap = 1;
                blockCount = ((endX - startX + 1 - 2 * gap) * (endY - startY + 1) * (endZ - startZ + 1 - 2 * gap));
            }

            return blockCount;
        }

        return 0;
    }

    private static String convertToMinecraftMaterial(String material) {
        return "minecraft:" + material.toLowerCase();
    }
}