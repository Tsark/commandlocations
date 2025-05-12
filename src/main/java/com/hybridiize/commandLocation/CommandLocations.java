package com.hybridiize.commandLocation; // Replace com.yourusername

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar; // For later use
import org.bukkit.scheduler.BukkitRunnable; // For later

public class CommandLocations extends JavaPlugin implements Listener {

    private FileConfiguration pluginConfig;
    private File configFile;

    // In-memory storage for quick access
    // Structure: GroupName -> GroupData
    private final Map<String, GroupData> groups = new HashMap<>();
    // Structure: PlayerUUID -> (GroupName -> BukkitTask for countdown)
    private final Map<UUID, Map<String, PlayerCountdownTask>> playerActiveCountdowns = new HashMap<>();
    // Structure: PlayerUUID -> (GroupName -> Timestamp of entry for resuming (more advanced))
    // For simplicity, we'll reset countdowns on leaving.
    public boolean isPlayerStillInGroupArea(Player player, GroupData group) {
        for (AreaData area : group.getAreas().values()) {
            if (isPlayerInArea(player, area, player.getLocation())) {
                return true;
            }
        }
        return false;
    }

    public void removePlayerActiveCountdown(UUID playerUUID, String groupName) {
        if (playerActiveCountdowns.containsKey(playerUUID)) {
            playerActiveCountdowns.get(playerUUID).remove(groupName);
            if (playerActiveCountdowns.get(playerUUID).isEmpty()) {
                playerActiveCountdowns.remove(playerUUID);
            }
        }
    }
    public Map<UUID, Float> getPlayerOriginalExp() { return playerOriginalExp; }
    public Map<UUID, Integer> getPlayerOriginalLevel() { return playerOriginalLevel; }
    public Map<UUID, BossBar> getPlayerBossBars() { return playerBossBars; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    // Hooks
    private Economy vaultEconomy = null;
    private boolean placeholderApiEnabled = false;

    // For managing active countdown displays (BossBars, player original XP, etc.)
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, Float> playerOriginalExp = new HashMap<>();
    private final Map<UUID, Integer> playerOriginalLevel = new HashMap<>();

    private int defaultAreaRadius; // Example: 1 means a 3x3x3 area

    @Override
    public void onEnable() {
        // Setup config
        setupConfig();
        setupVault();
        setupPlaceholderAPI();
        loadPluginData(); // Load after hooks so data can be processed if needed

        // Register commands
        CommandExecutor clocalCommand = new ClocalCommand(this);
        this.getCommand("clocal").setExecutor(clocalCommand);
        this.getCommand("clocal").setTabCompleter(new ClocalTabCompleter(this)); // Optional: For tab completion

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CommandLocations plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel all active countdowns & clear displays
        playerActiveCountdowns.values().forEach(groupMap -> groupMap.values().forEach(task -> {
            if (task instanceof BukkitRunnable) { // If we switch to BukkitRunnable
                task.cancel();
            } else if (task instanceof org.bukkit.scheduler.BukkitTask) { // Current setup
                ((org.bukkit.scheduler.BukkitTask)task).cancel();
            }
        }));
        playerActiveCountdowns.clear();

        // Clean up BossBars
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                entry.getValue().removePlayer(p);
            }
        }
        playerBossBars.clear();

        // Restore XP (important!)
        for (Map.Entry<UUID, Float> entry : playerOriginalExp.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.setExp(entry.getValue());
                if(playerOriginalLevel.containsKey(p.getUniqueId())) {
                    p.setLevel(playerOriginalLevel.get(p.getUniqueId()));
                }
            }
        }
        playerOriginalExp.clear();
        playerOriginalLevel.clear();

        // It's good practice to save data if necessary, though config.yml is primary storage
        // For this plugin, saving on disable might not be strictly needed if all changes are saved immediately.
        getLogger().info("CommandLocations plugin has been disabled!");
    }

    private void setupConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig(); // This saves the config.yml from your resources if it's there
        }
        pluginConfig = getConfig(); // Loads the config

        // Ensure default_area_radius exists
        pluginConfig.addDefault("default_area_radius", 1); // Default to 1 (3x3x3 area)
        pluginConfig.addDefault("groups", new HashMap<>()); // Ensure groups section exists
        pluginConfig.options().copyDefaults(true);
        savePluginConfig(); // Save any defaults that were added
    }

    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found! Economy features will be disabled.");
            return;
        }
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault_Economy provider not found! Economy features will be disabled.");
            return;
        }
        vaultEconomy = rsp.getProvider();
        if (vaultEconomy != null) {
            getLogger().info("Successfully hooked into Vault for economy features.");
        }
    }

    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderApiEnabled = true;
            getLogger().info("Successfully hooked into PlaceholderAPI.");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not be parsed.");
        }
    }

    // Helper to parse PAPI placeholders and color codes
    public String formatMessage(Player player, String message, GroupData group, int timeLeft) {
        if (message == null || message.isEmpty()) return "";

        // Custom Placeholders
        message = message.replace("%clocal_group_name%", group.getName());
        message = message.replace("%clocal_countdown%", String.valueOf(group.getCountdown()));
        message = message.replace("%clocal_time_left%", String.valueOf(timeLeft));
        message = message.replace("%clocal_price%", String.valueOf(group.getPrice()));


        if (placeholderApiEnabled && player != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    // Overload for messages not specific to a running countdown
    public String formatMessage(Player player, String message, GroupData group) {
        return formatMessage(player, message, group, group.getCountdown());
    }

    public void loadPluginData() {
        groups.clear(); // Clear current in-memory data
        defaultAreaRadius = pluginConfig.getInt("default_area_radius", 1);

        ConfigurationSection groupsSection = pluginConfig.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                ConfigurationSection groupSec = groupsSection.getConfigurationSection(groupName);
                if (groupSec == null) continue;

                Map<String, AreaData> areas = new HashMap<>();
                ConfigurationSection areasSection = groupSec.getConfigurationSection("areas");
                if (areasSection != null) {
                    for (String areaName : areasSection.getKeys(false)) {
                        ConfigurationSection areaSec = areasSection.getConfigurationSection(areaName);
                        if (areaSec != null) {
                            String world = areaSec.getString("world");
                            double x = areaSec.getDouble("x");
                            double y = areaSec.getDouble("y");
                            double z = areaSec.getDouble("z");
                            // int radius = areaSec.getInt("radius", defaultAreaRadius); // If you want per-area radius
                            areas.put(areaName, new AreaData(areaName, world, x, y, z, defaultAreaRadius));
                        }
                    }
                }

                List<String> commands = groupSec.getStringList("commands");
                int countdown = groupSec.getInt("countdown", 10);
                double price = groupSec.getDouble("price", 0.0);
                String enterMessage = groupSec.getString("enter_message", "&aEntering %clocal_group_name%...");
                String exitMessage = groupSec.getString("exit_message", "&cLeaving %clocal_group_name%...");
                CountdownType countdownType = CountdownType.fromString(groupSec.getString("countdown_type", "SMALL_TITLE"));

                GroupData.CountdownDisplayOptions displayOptions = new GroupData.CountdownDisplayOptions();
                ConfigurationSection displaySec = groupSec.getConfigurationSection("countdown_display_options");
                if (displaySec != null) {
                    displayOptions.setPlainTextFormat(displaySec.getString("plain_text_format", displayOptions.getPlainTextFormat()));
                    displayOptions.setBossBarTitle(displaySec.getString("boss_bar_title", displayOptions.getBossBarTitle()));
                    try {
                        displayOptions.setBossBarColor(BarColor.valueOf(displaySec.getString("boss_bar_color", "BLUE").toUpperCase()));
                        displayOptions.setBossBarStyle(BarStyle.valueOf(displaySec.getString("boss_bar_style", "SOLID").toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid BossBar color/style for group " + groupName + ". Using defaults.");
                    }
                    displayOptions.setTitleText(displaySec.getString("title_text", displayOptions.getTitleText()));
                    displayOptions.setSubtitleText(displaySec.getString("subtitle_text", displayOptions.getSubtitleText()));
                    displayOptions.setTitleFadeIn(displaySec.getInt("title_fade_in", displayOptions.getTitleFadeIn()));
                    displayOptions.setTitleStay(displaySec.getInt("title_stay", displayOptions.getTitleStay()));
                    displayOptions.setTitleFadeOut(displaySec.getInt("title_fade_out", displayOptions.getTitleFadeOut()));
                }


                groups.put(groupName, new GroupData(groupName, areas, commands, countdown, price, enterMessage, exitMessage, countdownType, displayOptions));
                getLogger().info("Loaded group: " + groupName + " with " + areas.size() + " areas and " + commands.size() + " commands.");
            }
        }
        getLogger().info("Loaded " + groups.size() + " groups from config.");
    }

    public void savePluginConfig() {
        try {
            pluginConfig.save(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save config.yml!", e);
        }
    }

    public FileConfiguration getPluginConfig() {
        return pluginConfig;
    }

    public Map<String, GroupData> getGroups() {
        return groups;
    }

    public int getDefaultAreaRadius() {
        return defaultAreaRadius;
    }

    // --- Player Movement and Countdown Logic ---

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        //getLogger().info("[CL DEBUG] onPlayerMove for " + player.getName() + " from (" + (from != null ? from.getBlockX() : "null") + ") to (" + (to != null ? to.getBlockX() : "null") + ")");

        // Optimization: only check if player actually moved to a new block
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ() && from.getWorld().equals(to.getWorld()))) {
            return;
        }
        //getLogger().info("[CL DEBUG] Player " + player.getName() + " moved to a new block. Processing...");

        UUID playerUUID = player.getUniqueId();

        for (GroupData group : groups.values()) {
            boolean playerIsInThisGroupArea = false;
            AreaData currentAreaPlayerIsIn = null;

            for (AreaData area : group.getAreas().values()) {
                if (isPlayerInArea(player, area, to)) {
                    playerIsInThisGroupArea = true;
                    //getLogger().info("[CL DEBUG] Player " + player.getName() + " is DETECTED IN an area of group " + group.getName() + " (Area: " + area.getName() + ")");
                    currentAreaPlayerIsIn = area; // The specific area they are in (could be multiple in same group)
                    break; // Found player in an area of this group
                }
            }

            boolean wasPlayerInThisGroupAreaPreviously = isPlayerInGroupAreaPreviously(player, group, from); // Player's previous location
            if (wasPlayerInThisGroupAreaPreviously) {
               // getLogger().info("[CL DEBUG] Player " + player.getName() + " WAS PREVIOUSLY IN an area of group " + group.getName());
            }


            // Player entered an area of this group
            if (playerIsInThisGroupArea && !wasPlayerInThisGroupAreaPreviously) {
               // getLogger().info("[CL DEBUG] Player " + player.getName() + " ENTERED group " + group.getName() + ". Attempting to start countdown.");
                startCountdown(player, group);
            }
            // Player left all areas of this group
            else if (!playerIsInThisGroupArea && wasPlayerInThisGroupAreaPreviously) {
              //  getLogger().info("[CL DEBUG] Player " + player.getName() + " LEFT group " + group.getName() + ". Attempting to cancel countdown.");
                cancelCountdown(player, group.getName());
            }

            // Player entered an area of this group
            if (playerIsInThisGroupArea && !isPlayerInGroupAreaPreviously(player, group, from)) {
                startCountdown(player, group);
            }
            // Player left all areas of this group
            else if (!playerIsInThisGroupArea && isPlayerInGroupAreaPreviously(player, group, from)) {
                cancelCountdown(player, group.getName());
            }
        }
    }

    private boolean isPlayerInGroupAreaPreviously(Player player, GroupData group, Location previousLocation) {
        for (AreaData area : group.getAreas().values()) {
            if (isPlayerInArea(player, area, previousLocation)) {
                return true;
            }
        }
        return false;
    }


    private boolean isPlayerInArea(Player player, AreaData area, Location location) {
        if (location == null || !location.getWorld().getName().equals(area.getWorldName())) {
           // getLogger().warning("[CL DEBUG] isPlayerInArea: Player location is null for area " + area.getName());
            return false;
        }
        // DEBUG: Print detailed info for every check
       // getLogger().info(String.format("[CL DEBUG] isPlayerInArea Check: Player '%s' at W:%s (%d,%d,%d) | Area '%s' Center W:%s (%d,%d,%d) Radius:%d",
               // player.getName(),
         //       location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
           //     area.getName(),
            //    area.getWorldName(), (int)area.getX(), (int)area.getY(), (int)area.getZ(), area.getRadius()
       // ));

        if (!location.getWorld().getName().equals(area.getWorldName())) {
           // getLogger().info("[CL DEBUG] isPlayerInArea: World mismatch. Player: " + location.getWorld().getName() + ", Area: " + area.getWorldName());
            return false;
        }

        // Simple cuboid check based on radius from the center block
        // Center of the area is (area.getX(), area.getY(), area.getZ())
        // Player location is (location.getX(), location.getY(), location.getZ())
        // The radius defines how many blocks out from the center block.
        // So, a radius of 0 is just the block itself. Radius 1 is 3x3x3.
        double minX = area.getX() - area.getRadius();
        double maxX = area.getX() + area.getRadius() + 1; // +1 because coords are corner of block
        double minY = area.getY() - area.getRadius();
        double maxY = area.getY() + area.getRadius() + 1;
        double minZ = area.getZ() - area.getRadius();
        double maxZ = area.getZ() + area.getRadius() + 1;

        boolean inArea = location.getX() >= minX && location.getX() < maxX &&
                location.getY() >= minY && location.getY() < maxY &&
                location.getZ() >= minZ && location.getZ() < maxZ;

        if (inArea) {
           // getLogger().info("[CL DEBUG] isPlayerInArea: Player IS IN area " + area.getName());
        }
        // else { getLogger().info("[CL DEBUG] isPlayerInArea: Player is NOT IN area " + area.getName()); } // Can be noisy

        return inArea;
    }

    private void startCountdown(Player player, GroupData group) {
        UUID playerUUID = player.getUniqueId();
        String groupName = group.getName();

       // getLogger().info("[CL DEBUG] startCountdown called for player " + player.getName() + ", group " + group.getName()); // New debug

        playerActiveCountdowns.putIfAbsent(playerUUID, new HashMap<>());

        // If already counting down for this group, don't restart (unless specific logic dictates)
        if (playerActiveCountdowns.get(playerUUID).containsKey(groupName)) {
       //     getLogger().info("[CL DEBUG] Player " + player.getName() + " already has active countdown for group " + group.getName() + ". Not starting new one."); // New debug
            // player.sendMessage("Debug: Already have a countdown for group " + groupName);
            return;
        }
        PlayerCountdownTask task = new PlayerCountdownTask(this, player, group);
     //   getLogger().info("[CL DEBUG] Created PlayerCountdownTask for " + player.getName() + ", group " + group.getName()); // New debug

        task.runTaskTimer(this, 0L, 20L); // Run immediately, then every 20 ticks (1 second)
     //   getLogger().info("[CL DEBUG] Scheduled PlayerCountdownTask for " + player.getName() + ", group " + group.getName()); // New debug
        playerActiveCountdowns.get(playerUUID).put(groupName, task);
    }

    private void cancelCountdown(Player player, String groupName) {
        UUID playerUUID = player.getUniqueId();
        if (playerActiveCountdowns.containsKey(playerUUID) && playerActiveCountdowns.get(playerUUID).containsKey(groupName)) {
            playerActiveCountdowns.get(playerUUID).get(groupName).cancel();
            playerActiveCountdowns.get(playerUUID).remove(groupName);
            if (playerActiveCountdowns.get(playerUUID).isEmpty()) {
                playerActiveCountdowns.remove(playerUUID);
            }
            player.sendMessage(String.format("§cYou have left '%s'. Countdown reset.", groupName));
        }
    }

    // --- Data Holder Classes ---
    public static class GroupData {
        private final String name;
        private final Map<String, AreaData> areas; // AreaName -> AreaData
        private List<String> commands;
        private int countdown; // in seconds
        private double price;
        private String enterMessage;
        private String exitMessage;
        private CountdownType countdownType;
        private CountdownDisplayOptions displayOptions;


        public GroupData(String name, Map<String, AreaData> areas, List<String> commands, int countdown,
                         double price, String enterMessage, String exitMessage, CountdownType countdownType,
                         CountdownDisplayOptions displayOptions) { // Parameter type is GroupData.CountdownDisplayOptions
            this.name = name;
            this.areas = areas != null ? areas : new HashMap<>();
            this.commands = commands != null ? commands : new ArrayList<>();
            this.countdown = countdown;
            this.price = price;
            this.enterMessage = enterMessage != null ? enterMessage : "&aEntered %clocal_group_name%"; // Corrected default access
            this.exitMessage = exitMessage != null ? exitMessage : "&cLeft %clocal_group_name%";     // Corrected default access
            this.countdownType = countdownType != null ? countdownType : CountdownType.SMALL_TITLE;
            this.displayOptions = displayOptions != null ? displayOptions : new CountdownDisplayOptions(); // Instantiating GroupData.CountdownDisplayOptions
        }

        public String getName() { return name; }
        public Map<String, AreaData> getAreas() { return areas; }
        public List<String> getCommands() { return commands; }
        public int getCountdown() { return countdown; }
        public double getPrice() { return price;}
        public String getEnterMessage() { return enterMessage; }
        public String getExitMessage() { return exitMessage; }
        public CountdownType getCountdownType() { return countdownType; }
        public CountdownDisplayOptions getDisplayOptions() { return displayOptions; }
        public void setCommands(List<String> commands) { this.commands = commands; }
        public void setCountdown(int countdown) { this.countdown = countdown; }
        public void setPrice(double price) { this.price = price; }
        public void setEnterMessage(String enterMessage) { this.enterMessage = enterMessage; }
        public void setExitMessage(String exitMessage) { this.exitMessage = exitMessage; }
        public void setCountdownType(CountdownType countdownType) { this.countdownType = countdownType; }
        public void setDisplayOptions(CountdownDisplayOptions displayOptions) { this.displayOptions = displayOptions; }
        public static class CountdownDisplayOptions {
            String plainTextFormat;
            String bossBarTitle;
            org.bukkit.boss.BarColor bossBarColor; // Store as Bukkit enum
            org.bukkit.boss.BarStyle bossBarStyle; // Store as Bukkit enum
            String titleText;
            String subtitleText;
            int titleFadeIn;
            int titleStay;
            int titleFadeOut;

            // Constructor with defaults
            public CountdownDisplayOptions() {
                this.plainTextFormat = "&eCountdown: &c%clocal_time_left%s...";
                this.bossBarTitle = "&6%clocal_group_name% Active: &c%clocal_time_left%s";
                this.bossBarColor = org.bukkit.boss.BarColor.BLUE;
                this.bossBarStyle = org.bukkit.boss.BarStyle.SOLID;
                this.titleText = "&b%clocal_time_left%";
                this.subtitleText = "&7Prepare...";
                this.titleFadeIn = 10;
                this.titleStay = 20;
                this.titleFadeOut = 10;
            }

            // Getters and potentially setters if direct modification is desired outside config load
            public String getPlainTextFormat() { return plainTextFormat; }
            public String getBossBarTitle() { return bossBarTitle; }
            public org.bukkit.boss.BarColor getBossBarColor() { return bossBarColor; }
            public org.bukkit.boss.BarStyle getBossBarStyle() { return bossBarStyle; }
            public String getTitleText() { return titleText; }
            public String getSubtitleText() { return subtitleText; }
            public int getTitleFadeIn() { return titleFadeIn; }
            public int getTitleStay() { return titleStay; }
            public int getTitleFadeOut() { return titleFadeOut; }

            // Setters
            public void setPlainTextFormat(String plainTextFormat) { this.plainTextFormat = plainTextFormat; }
            public void setBossBarTitle(String bossBarTitle) { this.bossBarTitle = bossBarTitle; }
            public void setBossBarColor(org.bukkit.boss.BarColor bossBarColor) { this.bossBarColor = bossBarColor; }
            public void setBossBarStyle(org.bukkit.boss.BarStyle bossBarStyle) { this.bossBarStyle = bossBarStyle; }
            public void setTitleText(String titleText) { this.titleText = titleText; }
            public void setSubtitleText(String subtitleText) { this.subtitleText = subtitleText; }
            public void setTitleFadeIn(int titleFadeIn) { this.titleFadeIn = titleFadeIn; }
            public void setTitleStay(int titleStay) { this.titleStay = titleStay; }
            public void setTitleFadeOut(int titleFadeOut) { this.titleFadeOut = titleFadeOut; }

        }
    }

    public static class AreaData {
        private final String name;
        private final String worldName;
        private final double x, y, z; // Center block coordinates
        private final int radius; // How many blocks out from the center (0 = just the center block)

        public AreaData(String name, String worldName, double x, double y, double z, int radius) {
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        public String getName() { return name; }
        public String getWorldName() { return worldName; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public int getRadius() { return radius; }
    }
}