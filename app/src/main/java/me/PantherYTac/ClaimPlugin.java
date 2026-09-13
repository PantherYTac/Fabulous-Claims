package me.PantherYTac;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class ClaimPlugin extends JavaPlugin {
    private static ClaimPlugin instance;

    private ClaimManager manager;
    private ClaimGUI gui;
    private ClaimBlockManager blockManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        manager = new ClaimManager(this);
        manager.load();

        gui = new ClaimGUI(this, manager);
        blockManager = new ClaimBlockManager(this);
        hologramManager = new HologramManager(this, manager);

        ClaimCommand claimCommand = new ClaimCommand(this, manager, gui, blockManager);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("claim").setTabCompleter(claimCommand);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(manager, blockManager), this);
        getServer().getPluginManager().registerEvents(new ClaimEnterLeaveListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new ClaimMobProtectionListener(manager), this);
        getServer().getPluginManager().registerEvents(new ClaimPvPListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new Visualization(), this);

        Visualization.init(this);

        getLogger().info("FabulousClaims enabled.");
    }

    @Override
    public void onDisable() {
        Visualization.shutdown();
        if (hologramManager != null) hologramManager.removeAllHolograms();
        if (manager != null) manager.save();
        getLogger().info("FabulousClaims disabled.");
    }

    public String getPrefix() {
        return getConfig().getString("prefix", "&6&lFabulousClaims &8&l|| &r").replace("&", "§");
    }

    public void sendPrefixed(Player player, String message) {
        player.sendMessage(getPrefix() + message);
    }

    public void sendPrefixed(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + message);
    }

    public static ClaimPlugin getInstance() { return instance; }
    public ClaimManager getManager() { return manager; }
    public ClaimGUI getGui() { return gui; }
    public ClaimBlockManager getBlockManager() { return blockManager; }
    public HologramManager getHologramManager() { return hologramManager; }

    /**
     * Utility method to check if claims are enabled in a given world.
     * Reads from config.yml -> world-claims
     */
    public boolean isClaimsEnabled(World world) {
        ConfigurationSection section = getConfig().getConfigurationSection("world-claims");
        if (section == null) return true; // default allow everywhere

        boolean whitelistMode = section.getBoolean("whitelist-mode", true);
        List<String> worlds = section.getStringList("worlds");

        if (whitelistMode) {
            // Only listed worlds allow claims
            return worlds.contains(world.getName());
        } else {
            // Listed worlds disallow claims
            return !worlds.contains(world.getName());
        }
    }

    public boolean feature(String path, boolean def) {
        return getConfig().getBoolean("features." + path, def);
    }
}
