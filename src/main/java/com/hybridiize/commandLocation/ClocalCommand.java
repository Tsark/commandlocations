package com.hybridiize.commandLocation; // Replace com.yourusername

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class ClocalCommand implements CommandExecutor {

    private final CommandLocations plugin;

    public ClocalCommand(CommandLocations plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("commandlocations.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                // /clocal create [name] [group name] [countdown duration]
                if (args.length == 4) {
                    handleCreateArea(player, args[1], args[2], args[3]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal create <areaName> <groupName> <countdownSeconds>");
                }
                break;
            case "command":
                if (args.length >= 4 && args[1].equalsIgnoreCase("add")) {
                    // /clocal command [group name] add [command]
                    String groupName = args[2];
                    StringBuilder cmdBuilder = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        cmdBuilder.append(args[i]).append(" ");
                    }
                    handleCommandAdd(player, groupName, cmdBuilder.toString().trim());
                } else if (args.length == 4 && args[1].equalsIgnoreCase("remove")) {
                    // /clocal command [group name] remove [number on list]
                    handleCommandRemove(player, args[2], args[3]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal command <groupName> add <commandString>");
                    player.sendMessage(ChatColor.RED + "Usage: /clocal command <groupName> remove <listNumber>");
                }
                break;
            case "list":
                if (args.length == 1) {
                    // /clocal list (all areas and groups)
                    handleListAll(player);
                } else if (args.length == 2) {
                    // /clocal list [group name] (commands in a group)
                    handleListGroupCommands(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal list OR /clocal list <groupName>");
                }
                break;
            case "areadelete":
                if (args.length == 2) {
                    handleAreaDelete(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal areadelete <areaName>");
                }
                break;
            case "groupdelete":
                if (args.length == 2) {
                    handleGroupDelete(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal groupdelete <groupName>");
                }
                break;
            case "reload":
                plugin.loadPluginData();
                player.sendMessage(ChatColor.GREEN + "CommandLocations configuration reloaded!");
                break;
            case "set":
                // /clocal set [group name] [parameter] [value...]
                if (args.length >= 4) {
                    handleSetGroupParameter(player, args[1], args[2].toLowerCase(), Arrays.copyOfRange(args, 3, args.length));
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /clocal set <groupName> <parameter> <value...>");
                    player.sendMessage(ChatColor.GRAY + "Parameters: countdown, entermessage, exitmessage, countdowntype, price, bossbartitle, bossbarcolor, bossbarstyle, titletext, subtitletext, plaintexformat");
                }
                break;
            default:
                sendHelpMessage(player);
                break;


        }
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- CommandLocations Help ---");
        player.sendMessage(ChatColor.AQUA + "/clocal create <areaName> <groupName> <countdown> " + ChatColor.GRAY + "- Creates an area.");
        player.sendMessage(ChatColor.AQUA + "/clocal command <group> add <command> " + ChatColor.GRAY + "- Adds a command to a group.");
        player.sendMessage(ChatColor.AQUA + "/clocal command <group> remove <#> " + ChatColor.GRAY + "- Removes a command from a group.");
        player.sendMessage(ChatColor.AQUA + "/clocal list " + ChatColor.GRAY + "- Lists all areas and groups.");
        player.sendMessage(ChatColor.AQUA + "/clocal list <group> " + ChatColor.GRAY + "- Lists commands for a group.");
        player.sendMessage(ChatColor.AQUA + "/clocal areadelete <areaName> " + ChatColor.GRAY + "- Deletes an area.");
        player.sendMessage(ChatColor.AQUA + "/clocal groupdelete <groupName> " + ChatColor.GRAY + "- Deletes a group and its areas.");
        player.sendMessage(ChatColor.AQUA + "/clocal reload " + ChatColor.GRAY + "- Reloads the plugin configuration.");

    }

    private void handleCreateArea(Player player, String areaName, String groupName, String countdownStr) {
        Block targetBlock = player.getTargetBlockExact(10); // Max distance of 10 blocks
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You are not looking at a block, or it's too far away.");
            return;
        }

        int countdown;
        try {
            countdown = Integer.parseInt(countdownStr);
            if (countdown <= 0) {
                player.sendMessage(ChatColor.RED + "Countdown duration must be a positive number.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid countdown duration: " + countdownStr);
            return;
        }

        // Path in config: groups.[groupName].areas.[areaName]
        // Path in config: groups.[groupName].countdown
        // Path in config: groups.[groupName].commands

        ConfigurationSection groupSection = plugin.getPluginConfig().getConfigurationSection("groups." + groupName);
        if (groupSection == null) {
            groupSection = plugin.getPluginConfig().createSection("groups." + groupName);
            groupSection.set("commands", new ArrayList<String>()); // Initialize empty command list for new group
        }

        // Check if area name already exists globally (optional, but good practice for unique area names)
        for (Map.Entry<String, CommandLocations.GroupData> entry : plugin.getGroups().entrySet()) {
            if (entry.getValue().getAreas().containsKey(areaName)) {
                player.sendMessage(ChatColor.RED + "An area with the name '" + areaName + "' already exists in group '" + entry.getKey() + "'. Area names must be unique.");
                return;
            }
        }


        ConfigurationSection areaSection = groupSection.createSection("areas." + areaName);
        Location loc = targetBlock.getLocation();
        areaSection.set("world", loc.getWorld().getName());
        areaSection.set("x", loc.getBlockX()); // Store as integer block coordinates
        areaSection.set("y", loc.getBlockY());
        areaSection.set("z", loc.getBlockZ());
        // areaSection.set("radius", plugin.getDefaultAreaRadius()); // Or a configurable radius per area

        // Set/update the group's countdown
        groupSection.set("countdown", countdown);

        plugin.savePluginConfig();
        plugin.loadPluginData(); // Reload in-memory data

        player.sendMessage(ChatColor.GREEN + "Area '" + areaName + "' created in group '" + groupName +
                "' at your target block with a countdown of " + countdown + " seconds for the group.");
    }

    private void handleCommandAdd(Player player, String groupName, String commandToAdd) {
        ConfigurationSection groupSection = plugin.getPluginConfig().getConfigurationSection("groups." + groupName);
        if (groupSection == null) {
            player.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist.");
            return;
        }

        List<String> commands = groupSection.getStringList("commands");
        commands.add(commandToAdd); // Command can include "/"
        groupSection.set("commands", commands);

        plugin.savePluginConfig();
        plugin.loadPluginData();

        player.sendMessage(ChatColor.GREEN + "Command added to group '" + groupName + "'.");
    }

    private void handleCommandRemove(Player player, String groupName, String numberStr) {
        ConfigurationSection groupSection = plugin.getPluginConfig().getConfigurationSection("groups." + groupName);
        if (groupSection == null) {
            player.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist.");
            return;
        }

        List<String> commands = groupSection.getStringList("commands");
        if (commands.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Group '" + groupName + "' has no commands to remove.");
            return;
        }

        int indexToRemove;
        try {
            indexToRemove = Integer.parseInt(numberStr) - 1; // User inputs 1-based index
            if (indexToRemove < 0 || indexToRemove >= commands.size()) {
                player.sendMessage(ChatColor.RED + "Invalid command number. Use /clocal list " + groupName + " to see valid numbers.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid command number: " + numberStr);
            return;
        }

        String removedCommand = commands.remove(indexToRemove);
        groupSection.set("commands", commands);
        plugin.savePluginConfig();
        plugin.loadPluginData();

        player.sendMessage(ChatColor.GREEN + "Removed command '" + removedCommand + "' from group '" + groupName + "'.");
    }


    private void handleListGroupCommands(Player player, String groupName) {
        CommandLocations.GroupData groupData = plugin.getGroups().get(groupName);
        if (groupData == null) {
            player.sendMessage(ChatColor.RED + "Group '" + groupName + "' not found.");
            return;
        }

        List<String> commands = groupData.getCommands();
        if (commands.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Group '" + groupName + "' has no registered commands.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "--- Commands for Group: " + ChatColor.AQUA + groupName + ChatColor.GOLD + " --- (Countdown: " + groupData.getCountdown() + "s)");
        for (int i = 0; i < commands.size(); i++) {
            player.sendMessage(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.WHITE + commands.get(i));
        }
    }

    private void handleListAll(Player player) {
        Map<String, CommandLocations.GroupData> allGroups = plugin.getGroups();
        if (allGroups.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No command locations or groups have been created yet.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "--- All Command Locations ---");
        for (CommandLocations.GroupData group : allGroups.values()) {
            player.sendMessage(ChatColor.DARK_AQUA + "Group: " + ChatColor.AQUA + group.getName() +
                    ChatColor.GRAY + " (Countdown: " + group.getCountdown() + "s)");
            if (group.getAreas().isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "  - No areas defined for this group.");
            } else {
                for (CommandLocations.AreaData area : group.getAreas().values()) {
                    player.sendMessage(ChatColor.GRAY + "  - Area: " + ChatColor.WHITE + area.getName() +
                            ChatColor.GRAY + " (World: " + area.getWorldName() +
                            ", Coords: " + (int)area.getX() + "," + (int)area.getY() + "," + (int)area.getZ() +
                            ", Radius: " + area.getRadius() + ")");
                }
            }
        }
    }

    private void handleAreaDelete(Player player, String areaNameToDelete) {
        String groupOfArea = null;
        CommandLocations.GroupData groupDataFound = null;

        for (Map.Entry<String, CommandLocations.GroupData> entry : plugin.getGroups().entrySet()) {
            if (entry.getValue().getAreas().containsKey(areaNameToDelete)) {
                groupOfArea = entry.getKey();
                groupDataFound = entry.getValue();
                break;
            }
        }

        if (groupOfArea == null || groupDataFound == null) {
            player.sendMessage(ChatColor.RED + "Area '" + areaNameToDelete + "' not found.");
            return;
        }

        plugin.getPluginConfig().set("groups." + groupOfArea + ".areas." + areaNameToDelete, null); // Remove from config
        plugin.savePluginConfig();
        plugin.loadPluginData(); // Reload

        player.sendMessage(ChatColor.GREEN + "Area '" + areaNameToDelete + "' has been deleted from group '" + groupOfArea + "'.");
    }

    private void handleGroupDelete(Player player, String groupNameToDelete) {
        if (!plugin.getGroups().containsKey(groupNameToDelete)) {
            player.sendMessage(ChatColor.RED + "Group '" + groupNameToDelete + "' not found.");
            return;
        }

        plugin.getPluginConfig().set("groups." + groupNameToDelete, null); // Remove entire group from config
        plugin.savePluginConfig();
        plugin.loadPluginData(); // Reload

        player.sendMessage(ChatColor.GREEN + "Group '" + groupNameToDelete + "' and all its areas have been deleted.");
    }
    private void handleSetGroupParameter(Player player, String groupName, String param, String[] valueArgs) {
        ConfigurationSection groupSection = plugin.getPluginConfig().getConfigurationSection("groups." + groupName);
        if (groupSection == null) {
            player.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist.");
            return;
        }

        String value = String.join(" ", valueArgs); // Join all remaining args for messages/multi-word values

        switch (param) {
            case "countdown":
                try {
                    int cd = Integer.parseInt(value);
                    if (cd <= 0) {
                        player.sendMessage(ChatColor.RED + "Countdown must be a positive number."); return;
                    }
                    groupSection.set("countdown", cd);
                    player.sendMessage(ChatColor.GREEN + "Countdown for group '" + groupName + "' set to " + cd + "s.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number for countdown."); return;
                }
                break;
            case "price":
                try {
                    double price = Double.parseDouble(value);
                    if (price < 0) {
                        player.sendMessage(ChatColor.RED + "Price cannot be negative."); return;
                    }
                    groupSection.set("price", price);
                    player.sendMessage(ChatColor.GREEN + "Price for group '" + groupName + "' set to " + price + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number for price."); return;
                }
                break;
            case "entermessage":
                groupSection.set("enter_message", value);
                player.sendMessage(ChatColor.GREEN + "Enter message for group '" + groupName + "' set.");
                break;
            case "exitmessage":
                groupSection.set("exit_message", value);
                player.sendMessage(ChatColor.GREEN + "Exit message for group '" + groupName + "' set.");
                break;
            case "countdowntype":
                try {
                    CountdownType ct = CountdownType.valueOf(value.toUpperCase());
                    groupSection.set("countdown_type", ct.name());
                    player.sendMessage(ChatColor.GREEN + "Countdown type for group '" + groupName + "' set to " + ct.name() + ".");
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid countdown type. Options: EXP_BAR, PLAIN_TEXT, BOSS_BAR, SMALL_TITLE, LARGE_TITLE"); return;
                }
                break;
            // Countdown Display Options - nested under 'countdown_display_options'
            case "plaintexformat":
                groupSection.set("countdown_display_options.plain_text_format", value);
                player.sendMessage(ChatColor.GREEN + "Plain text format for group '" + groupName + "' set.");
                break;
            case "bossbartitle":
                groupSection.set("countdown_display_options.boss_bar_title", value);
                player.sendMessage(ChatColor.GREEN + "Boss bar title for group '" + groupName + "' set.");
                break;
            case "bossbarcolor":
                try {
                    BarColor bc = BarColor.valueOf(value.toUpperCase());
                    groupSection.set("countdown_display_options.boss_bar_color", bc.name());
                    player.sendMessage(ChatColor.GREEN + "Boss bar color for group '" + groupName + "' set to " + bc.name() + ".");
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid BossBar Color. (RED, BLUE, GREEN, PINK, PURPLE, WHITE, YELLOW)"); return;
                }
                break;
            case "bossbarstyle":
                try {
                    BarStyle bs = BarStyle.valueOf(value.toUpperCase());
                    groupSection.set("countdown_display_options.boss_bar_style", bs.name());
                    player.sendMessage(ChatColor.GREEN + "Boss bar style for group '" + groupName + "' set to " + bs.name() + ".");
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid BossBar Style. (SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20)"); return;
                }
                break;
            case "titletext":
                groupSection.set("countdown_display_options.title_text", value);
                player.sendMessage(ChatColor.GREEN + "Title text for group '" + groupName + "' set.");
                break;
            case "subtitletext":
                groupSection.set("countdown_display_options.subtitle_text", value);
                player.sendMessage(ChatColor.GREEN + "Subtitle text for group '" + groupName + "' set.");
                break;
            // You can add cases for title_fade_in, title_stay, title_fade_out if desired.
            default:
                player.sendMessage(ChatColor.RED + "Unknown parameter: " + param);
                player.sendMessage(ChatColor.GRAY + "Parameters: countdown, entermessage, exitmessage, countdowntype, price, bossbartitle, bossbarcolor, bossbarstyle, titletext, subtitletext, plaintexformat");
                return;
        }

        plugin.savePluginConfig();
        plugin.loadPluginData(); // Reload data to reflect changes immediately
    }


}