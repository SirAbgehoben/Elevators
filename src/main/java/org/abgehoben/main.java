package org.abgehoben;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;


public final class main extends JavaPlugin implements Listener {

    private elevators elevators;
    private commands commands;

    @Override
    public void onEnable() {

        elevators = new elevators(this);
        commands = new commands();

        getCommand("map").setExecutor(commands);

        getServer().getPluginManager().registerEvents(elevators, this);



        // Create chunks folder if it doesn't exist (might not be needed anymore)
        File chunksFolder = new File(getDataFolder(), "chunks");
        if (!chunksFolder.exists()) {
            chunksFolder.mkdirs();
        }



        getLogger().info("Plugin enabled.");
    }

    @Override
    public void onDisable() {
        // Save all elevator data to the elevators.yml file
        elevators.DisableElevators();

        getLogger().info("Plugin disabled.");
    }

    public elevators getElevators() {
        return elevators;
    }

}
