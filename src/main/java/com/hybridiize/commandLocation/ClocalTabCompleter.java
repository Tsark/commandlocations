package com.hybridiize.commandLocation; // Replace com.yourusername

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClocalTabCompleter implements TabCompleter {

    private final CommandLocations plugin;
    private static final List<String> SUBCOMMANDS_LVL1 = Arrays.asList("create", "command", "list", "areadelete", "groupdelete", "reload");
    private static final List<String> SUBCOMMANDS_COMMAND_LVL2 = Arrays.asList("add", "remove");
    private static final List<String> COUNTDOWN_TYPES = Arrays.stream(CountdownType.values()).map(Enum::name).collect(Collectors.toList());
    private static final List<String> BOSSBAR_COLORS = Arrays.stream(BarColor.values()).map(Enum::name).collect(Collectors.toList());
    private static final List<String> BOSSBAR_STYLES = Arrays.stream(BarStyle.values()).map(Enum::name).collect(Collectors.toList());


    public ClocalTabCompleter(CommandLocations plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS_LVL1, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("command")) {
                StringUtil.copyPartialMatches(args[1], SUBCOMMANDS_COMMAND_LVL2, completions);
            } else if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("groupdelete")) {
                StringUtil.copyPartialMatches(args[1], plugin.getGroups().keySet(), completions);
            } else if (args[0].equalsIgnoreCase("areadelete")) {
                List<String> allAreaNames = plugin.getGroups().values().stream()
                        .flatMap(groupData -> groupData.getAreas().keySet().stream())
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], allAreaNames, completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                StringUtil.copyPartialMatches(args[2], plugin.getGroups().keySet(), completions);
            } else if (args[0].equalsIgnoreCase("create")){
                // Suggest existing group names or <newGroupName>
                List<String> groupSuggestions = new ArrayList<>(plugin.getGroups().keySet());
                // groupSuggestions.add("<newGroupName>"); // Placeholder text
                StringUtil.copyPartialMatches(args[2], groupSuggestions, completions);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            StringUtil.copyPartialMatches(args[2], SET_PARAMETERS, completions);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            if (args[2].equalsIgnoreCase("countdowntype")) {
                StringUtil.copyPartialMatches(args[3], COUNTDOWN_TYPES, completions);
            } else if (args[2].equalsIgnoreCase("bossbarcolor")) {
                StringUtil.copyPartialMatches(args[3], BOSSBAR_COLORS, completions);
            } else if (args[2].equalsIgnoreCase("bossbarstyle")) {
                StringUtil.copyPartialMatches(args[3], BOSSBAR_STYLES, completions);
            }
        }
        // Add more levels for specific commands like /clocal command <groupName> remove <number> if needed
        // For <number>, you might list existing command numbers for that group.

        Collections.sort(completions);
        return completions;
    }

    private static final List<String> SET_PARAMETERS = Arrays.asList(
            "countdown", "price", "entermessage", "exitmessage", "countdowntype",
            "plaintexformat", "bossbartitle", "bossbarcolor", "bossbarstyle", "titletext", "subtitletext"
            // Add fadein/stay/out if you implement them in set command
    );





}