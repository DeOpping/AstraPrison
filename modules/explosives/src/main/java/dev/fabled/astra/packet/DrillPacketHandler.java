package dev.fabled.astra.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import dev.fabled.astra.Astra;
import dev.fabled.astra.items.DrillsItem;
import dev.fabled.astra.mines.generator.MineGenerator;
import dev.fabled.astra.utils.ExplosiveReset;
import dev.fabled.astra.utils.MineData;
import dev.fabled.astra.utils.MineReader;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.fabled.astra.utils.MineWriter.FILE;

public class DrillPacketHandler extends PacketListenerAbstract {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<UUID, Integer> activeDrills = new HashMap<>();

    public DrillPacketHandler() {
        super(PacketListenerPriority.NORMAL);
    }

    public static int getActiveDrills(UUID playerUUID) {
        return activeDrills.getOrDefault(playerUUID, 0);
    }

    private static void drillreset(Player player, Block block) {

        String mineName = block.getMetadata("mineName").get(0).asString();
        UUID userUUID = player.getUniqueId();
        ExplosiveReset.updateBlockCount(userUUID, mineName);
        if (ExplosiveReset.shouldResetMine(userUUID, mineName)) {
            ExplosiveReset.resetMine(player, mineName);
        }

    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            PacketReceiveEvent copy = event.clone();
            EXECUTOR.execute(() -> {
                WrapperPlayClientPlayerBlockPlacement drillingWrapper = new WrapperPlayClientPlayerBlockPlacement(copy);
                int blockX = drillingWrapper.getBlockPosition().getX();
                int blockY = drillingWrapper.getBlockPosition().getY();
                int blockZ = drillingWrapper.getBlockPosition().getZ();
                User user = copy.getUser();

                World world = Bukkit.getWorld("world");
                Block block = world.getBlockAt(blockX, blockY, blockZ);
                if (user != null) {
                    Player player = Bukkit.getPlayer(user.getUUID());
                    if (player != null && DrillsItem.isDrillsItem(player.getItemInHand())) {
                        player.setMetadata("drilling", new FixedMetadataValue(Astra.getPlugin(), true));
                        int drillSize = getDrillSize(player.getItemInHand());
                        Bukkit.getScheduler().runTask(Astra.getPlugin(), () -> {
                            String mineName = block.getLocation().getBlock().getMetadata("mineName").get(0).asString();
                            player.sendBlockChange(block.getLocation().add(0, 1, 0), Material.HOPPER.createBlockData());
                            startDrillAnimation(block, world, blockX, blockY, blockZ, user, mineName, player.getItemInHand(), Integer.valueOf(drillSize));
                        });
                    }
                }

                copy.cleanUp();
            });
        }
    }

    private void startDrillAnimation(Block block, World world, int x, int y, int z, User user, String mineName, ItemStack drillItem, int drillSize) {
        //int drillSize = getDrillSize(drillItem);

        new BukkitRunnable() {

            int currentY = y;
            int countblocks = 0;
            Location previousLocation = block.getLocation();

            @Override
            public void run() {
                if (currentY < 0) {
                    removePreviousHopper();
                    this.cancel();
                    return;
                }

                Player player = Bukkit.getPlayer(user.getUUID());
                if (player == null || !isPlayerMap(player, block)) {
                    removePreviousHopper();
                    this.cancel();
                    return;
                }

                if (currentY != y) {
                    removePreviousHopper();
                }

                Location location = new Location(world, x, currentY, z);
                previousLocation = location;

                ArrayList<BlockState> blockStates = new ArrayList<>();
                int halfSize = (drillSize - 1) / 2;

                for (int dx = -halfSize; dx <= halfSize; dx++) {
                    for (int dz = -halfSize; dz <= halfSize; dz++) {
                        for (int dy = -halfSize; dy <= halfSize; dy++) {
                            Block currentBlock = world.getBlockAt(x + dx, currentY + dy, z + dz);
                            if (isPlayerMap(player, currentBlock)) {
                                BlockState blockState = currentBlock.getState();
                                blockState.setType(Material.AIR);
                                blockStates.add(blockState);
                                drillreset(player, block);
                                //blockState.removeMetadata("material", Astra.getPlugin());
                                String blockmaterial = block.getMetadata("mineName").get(0).asString();
                                if (blockmaterial != null) {
                                countblocks++;
                                }
                                blockState.removeMetadata("material", Astra.getPlugin());
                            }
                        }
                    }
                }

                player.sendBlockChanges(blockStates);

                currentY--;
                String filePath = FILE;

                MineData mineData = MineReader.readMineData(filePath, mineName);

                if (currentY < mineData.getEndY()) {
                    removePreviousHopper();
                    player.sendTitle(ChatColor.GREEN + "Blocks mined: ", ChatColor.WHITE + String.valueOf(countblocks), 10, 70, 20);

                    countblocks = 0;
                    this.cancel();
                }
            }

            private boolean isPlayerMap(Player player, Block block) {
                List<UUID> playerUUIDsForBlock = MineGenerator.getPlayerUUIDsForBlock(block, player.getUniqueId());
                return playerUUIDsForBlock.contains(player.getUniqueId());
            }

            private void removePreviousHopper() {
                if (previousLocation != null) {
                    Player player = Bukkit.getPlayer(user.getUUID());
                    if (player != null) {
                        player.sendBlockChange(previousLocation, Material.AIR.createBlockData());
                        block.setType(Material.AIR);
                    }
                }
            }

        }.runTaskTimer(Astra.getPlugin(), 0L, 20L);
    }

    private int getDrillSize(ItemStack drillItem) {
        ItemMeta meta = drillItem.getItemMeta();
        JavaPlugin plugin = Astra.getPlugin();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = ChatColor.stripColor(meta.getDisplayName());
            if (DrillsItem.getdrillkey(drillItem, "normal_drill_item")) {
                return 3;
            } else if (DrillsItem.getdrillkey(drillItem, "big_drill_item")) {
                return 5;
            } else if (DrillsItem.getdrillkey(drillItem, "ultra_drill_item")) {
                return 9;
            }
        }
        return 3;
    }
}