package org.abgehoben;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

public class elevators implements @NotNull Listener {

    private static Economy econ = null;
    private final double ELEVATOR_COST = 500.0;
    private final int ELEVATOR_RANGE = 50;
    private Map<String, List<Location>> elevators = new HashMap<>();
    private final JavaPlugin plugin;

    public elevators(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin((Plugin) this);
            return;
        }
        // Load all elevator data from the elevators.yml file
        loadElevatorsData();
    }

    public void DisableElevators() {
        saveElevatorsData();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @EventHandler
    public void onPressurePlatePlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (block.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            if (econ == null) {
                player.sendMessage("§cThe economy plugin is not available. Elevators cannot be created.");
                event.setCancelled(true); // Cancel placement if no economy
                return;
            }

            String elevatorKey = block.getLocation().getBlockX() + "," + block.getLocation().getBlockZ();
            List<Location> elevatorsInColumn = elevators.getOrDefault(elevatorKey, new ArrayList<>());

            // Check if there's at least one other pressure plate at the same X and Z
            long otherElevatorsCount = elevatorsInColumn.stream()
                    .filter(loc -> loc.getBlockY() != block.getLocation().getBlockY())
                    .count();

            if (otherElevatorsCount > 0) {
                event.setCancelled(true); // Cancel placement and open GUI
                openElevatorConfirmationGUI(player, block.getLocation());
            } else {
                registerElevator(block.getLocation());
            }
        }
    }

    public void openElevatorConfirmationGUI(Player player, Location pressurePlateLocation) {
        Inventory gui = Bukkit.createInventory(null, 27, "Confirm Elevator Creation");

        ItemStack accept = createGuiItem(Material.GREEN_WOOL, "§aAccept and Pay " + ELEVATOR_COST);
        ItemStack cancel = createGuiItem(Material.RED_WOOL, "§cCancel");

        gui.setItem(11, cancel);
        gui.setItem(15, accept);

        player.openInventory(gui);
        player.setMetadata("pendingElevator", new FixedMetadataValue((Plugin) this, pressurePlateLocation));
    }

    private ItemStack createGuiItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().toLowerCase().equals("confirm elevator creation")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Optional<MetadataValue> metadataValue = player.getMetadata("pendingElevator")
                .stream()
                .filter(value -> value.getOwningPlugin() == this)
                .findFirst();

        if (!metadataValue.isPresent()) {
            player.sendMessage("§cNo pending elevator location found.");
            player.closeInventory();
            return;
        }

        Location pendingElevatorLocation = (Location) metadataValue.get().value();

        if (clickedItem.getType() == Material.GREEN_WOOL) {
            if (econ != null && econ.getBalance(player) >= ELEVATOR_COST) {
                econ.withdrawPlayer(player, ELEVATOR_COST);
                player.sendMessage("§a" + ELEVATOR_COST + " has been withdrawn for the elevator creation!");
                // Remove one pressure plate from inventory
                removePressurePlateFromInventory(player);
                // Place the pressure plate BEFORE closing the inventory
                pendingElevatorLocation.getBlock().setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);

                registerElevator(pendingElevatorLocation); // Register after placement
            } else {
                player.sendMessage("§cYou do not have enough money to create the elevator.");
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.sendMessage("§cElevator creation canceled.");
        }

        player.closeInventory();
        player.removeMetadata("pendingElevator", (Plugin) this);
    }


    private void registerElevator(Location location) {
        String elevatorKey = location.getBlockX() + "," + location.getBlockZ();

        if (!elevators.containsKey(elevatorKey)) {
            elevators.put(elevatorKey, new ArrayList<>());
        }

        elevators.get(elevatorKey).add(location);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to.getBlock().getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            String elevatorKey = to.getBlockX() + "," + to.getBlockZ();

            if (elevators.containsKey(elevatorKey) && elevators.get(elevatorKey).contains(to.getBlock().getLocation())) {

                if (to.getY() > from.getY()) {
                    movePlayerToNextElevator(player, to.getBlock().getLocation(), true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        Location location = player.getLocation().getBlock().getLocation();

        if (location.getBlock().getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            String elevatorKey = location.getBlockX() + "," + location.getBlockZ();

            if (elevators.containsKey(elevatorKey) && elevators.get(elevatorKey).contains(location)) {
                movePlayerToNextElevator(player, location, false);
            }
        }
    }

    private void movePlayerToNextElevator(Player player, Location currentElevator, boolean goingUp) {
        String elevatorKey = currentElevator.getBlockX() + "," + currentElevator.getBlockZ();
        List<Location> elevatorsInColumn = elevators.get(elevatorKey);

        if (elevatorsInColumn == null || elevatorsInColumn.size() <= 1) return;

        elevatorsInColumn.sort(Comparator.comparingDouble(Location::getY));

        int currentIndex = elevatorsInColumn.indexOf(currentElevator);

        Location nextElevator = null;
        if (goingUp) {
            for (int i = currentIndex + 1; i < elevatorsInColumn.size(); i++) {
                Location elevator = elevatorsInColumn.get(i);
                if (Math.abs(elevator.getY() - currentElevator.getY()) <= ELEVATOR_RANGE) {
                    nextElevator = elevator;
                    break;
                }
            }
        } else {
            for (int i = currentIndex - 1; i >= 0; i--) {
                Location elevator = elevatorsInColumn.get(i);
                if (Math.abs(elevator.getY() - currentElevator.getY()) <= ELEVATOR_RANGE) {
                    nextElevator = elevator;
                    break;
                }
            }
        }

        if (nextElevator != null) {

            Vector direction = player.getLocation().getDirection();
            Location teleportLocation = nextElevator.clone().add(0.5, 0, 0.5);
            teleportLocation.setDirection(direction);
            player.teleport(teleportLocation);
        }
    }

    private void loadElevatorsData() {
        File dataFile = new File(plugin.getDataFolder(), "elevators.yml");

        if (dataFile.exists()) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                for (String elevatorKey : config.getKeys(false)) {
                    List<String> elevatorStrings = config.getStringList(elevatorKey);
                    List<Location> elevatorLocations = new ArrayList<>();
                    for (String elevatorString : elevatorStrings) {
                        String[] parts = elevatorString.split(",");
                        if (parts.length == 4) {
                            try {
                                double x = Double.parseDouble(parts[0]);
                                double y = Double.parseDouble(parts[1]);
                                double z = Double.parseDouble(parts[2]);
                                String worldName = parts[3];
                                elevatorLocations.add(new Location(Bukkit.getWorld(worldName), x, y, z));
                            } catch (NumberFormatException e) {
                                getLogger().log(Level.WARNING, "Invalid elevator location format in data file: " + elevatorString, e);
                            }
                        }
                    }
                    elevators.put(elevatorKey, elevatorLocations);
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error loading elevator data from file: " + dataFile.getName(), e);
            }
        }
    }

    private void saveElevatorsData() {
        File dataFile = new File(plugin.getDataFolder(), "elevators.yml");
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, List<Location>> entry : elevators.entrySet()) {
            String elevatorKey = entry.getKey();
            List<String> elevatorStrings = new ArrayList<>();
            for (Location location : entry.getValue()) {
                elevatorStrings.add(location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getWorld().getName());
            }
            config.set(elevatorKey, elevatorStrings);
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error saving elevator data to file: " + dataFile.getName(), e);
        }
    }

    @EventHandler
    public void onPressurePlateBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            Location location = block.getLocation();
            String elevatorKey = location.getBlockX() + "," + location.getBlockZ();

            if (elevators.containsKey(elevatorKey)) {
                List<Location> elevatorsInColumn = elevators.get(elevatorKey);
                elevatorsInColumn.remove(location);

                if (elevatorsInColumn.isEmpty()) {
                    elevators.remove(elevatorKey);
                }
            }
        }
    }

    private void removePressurePlateFromInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                    break; // Found and reduced, exit loop
                } else {
                    inventory.setItem(i, null); // Only one left, remove entirely
                    break; // Found and removed, exit loop
                }
            }
        }
    }
}
