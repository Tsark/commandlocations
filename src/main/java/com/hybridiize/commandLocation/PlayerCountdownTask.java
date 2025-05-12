package com.hybridiize.commandLocation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.EconomyResponse;


public class PlayerCountdownTask extends BukkitRunnable {

    private final CommandLocations plugin;
    private final Player player;
    private final CommandLocations.GroupData group;
    private int timeLeft;
    private BossBar bossBar; // Only if type is BOSS_BAR
    private float originalExp;
    private int originalLevel;

    public PlayerCountdownTask(CommandLocations plugin, Player player, CommandLocations.GroupData group) {
        this.plugin = plugin;
        this.player = player;
        this.group = group;
        this.timeLeft = group.getCountdown();

        //plugin.getLogger().info("[CL DEBUG] PlayerCountdownTask CONSTRUCTOR for " + player.getName() + ", group " + group.getName() + ". Time: " + timeLeft); // New debug

        // Initialize display based on type
        initializeDisplay();

        // Send Enter Message
        String enterMsg = plugin.formatMessage(player, group.getEnterMessage(), group, timeLeft);
        if (!enterMsg.isEmpty()) {
            player.sendMessage(enterMsg); // You said messages work, this should appear
           // plugin.getLogger().info("[CL DEBUG] Sent enter message to " + player.getName() + " for group " + group.getName()); // New debug
      //  } else {
           // plugin.getLogger().info("[CL DEBUG] Enter message is empty for " + player.getName() + " for group " + group.getName()); // New debug
        }
    }

    private void initializeDisplay() {
        switch (group.getCountdownType()) {
            case EXP_BAR:
                originalExp = player.getExp();
                originalLevel = player.getLevel();
                break;
            case BOSS_BAR:
                String title = plugin.formatMessage(player, group.getDisplayOptions().getBossBarTitle(), group, timeLeft);
                bossBar = Bukkit.createBossBar(title, group.getDisplayOptions().getBossBarColor(), group.getDisplayOptions().getBossBarStyle());
                bossBar.addPlayer(player);
                plugin.getPlayerBossBars().put(player.getUniqueId(), bossBar); // Track for removal
                break;
            // SMALL_TITLE and LARGE_TITLE handled per tick. PLAIN_TEXT too.
        }
        updateDisplay(); // Initial display update
    }

    @Override
    public void run() {
       // plugin.getLogger().info("[CL DEBUG] PlayerCountdownTask RUN for " + player.getName() + ", group " + group.getName() + ". TimeLeft: " + timeLeft); // New debug
        if (!player.isOnline() || !plugin.isPlayerStillInGroupArea(player, group)) {
            //plugin.getLogger().info("[CL DEBUG] Player " + player.getName() + " is offline. Cancelling task for group " + group.getName());
            cancelCountdown(false); // Player left area or logged off
            return;
        }

        if (timeLeft <= 0) {
            executeCommands();
            cancelCountdown(true); // Countdown finished
            return;
        }

        //boolean stillInAreaCheck = plugin.isPlayerStillInGroupArea(player, group);
        //plugin.getLogger().info("[CL DEBUG] Player " + player.getName() + " still in area check for group " + group.getName() + ": " + stillInAreaCheck);

        //if (!stillInAreaCheck) {
           // plugin.getLogger().info("[CL DEBUG] Player " + player.getName() + " left area. Cancelling task for group " + group.getName());
           // cancelCountdown(false); // Player left area or logged off
          //  return;
       // }

        updateDisplay();
        timeLeft--;
    }

    private void updateDisplay() {
        float progress = Math.max(0.0f, (float) timeLeft / group.getCountdown());

        switch (group.getCountdownType()) {
            case EXP_BAR:
                player.setExp(progress);
                player.setLevel(timeLeft); // Show seconds as levels
                break;
            case PLAIN_TEXT:
                player.sendMessage(plugin.formatMessage(player, group.getDisplayOptions().getPlainTextFormat(), group, timeLeft));
                break;
            case BOSS_BAR:
                if (bossBar != null) {
                    bossBar.setProgress(progress);
                    bossBar.setTitle(plugin.formatMessage(player, group.getDisplayOptions().getBossBarTitle(), group, timeLeft));
                }
                break;
            case SMALL_TITLE:
                player.sendTitle(
                        plugin.formatMessage(player, group.getDisplayOptions().getTitleText(), group, timeLeft),
                        null, // No subtitle for small
                        group.getDisplayOptions().getTitleFadeIn(),
                        group.getDisplayOptions().getTitleStay(),
                        group.getDisplayOptions().getTitleFadeOut()
                );
                break;
            case LARGE_TITLE:
                player.sendTitle(
                        plugin.formatMessage(player, group.getDisplayOptions().getTitleText(), group, timeLeft),
                        plugin.formatMessage(player, group.getDisplayOptions().getSubtitleText(), group, timeLeft),
                        group.getDisplayOptions().getTitleFadeIn(),
                        group.getDisplayOptions().getTitleStay(),
                        group.getDisplayOptions().getTitleFadeOut()
                );
                break;
        }
    }

    private void executeCommands() {
        //plugin.getLogger().info("[CL DEBUG] EXECUTE_COMMANDS called for player " + player.getName() + ", group " + group.getName()); // New debug
        // Vault Price Check
        if (group.getPrice() > 0 && plugin.getVaultEconomy() != null) {
            //plugin.getLogger().info("[CL DEBUG] Checking price " + group.getPrice() + " for " + player.getName());
            if (plugin.getVaultEconomy().getBalance(player) < group.getPrice()) {
                player.sendMessage(ChatColor.RED + "You cannot afford the price of " + plugin.getVaultEconomy().format(group.getPrice()) + " to activate this.");
                //plugin.getLogger().info("[CL DEBUG] Player " + player.getName() + " cannot afford price. Commands not run.");
                return; // Don't run commands
            }
            EconomyResponse r = plugin.getVaultEconomy().withdrawPlayer(player, group.getPrice());
            if (!r.transactionSuccess()) {
                player.sendMessage(ChatColor.RED + "Could not withdraw " + plugin.getVaultEconomy().format(group.getPrice()) + ". Error: " + r.errorMessage);
                return; // Don't run commands
            }
            player.sendMessage(ChatColor.GREEN + "Paid " + plugin.getVaultEconomy().format(group.getPrice()) + ".");
        }

        // Execute commands
        player.sendMessage(ChatColor.GREEN + "Executing commands for group '" + group.getName() + "'!");
        for (String command : group.getCommands()) {
            String processedCommand = plugin.formatMessage(player, command, group, 0);
          //  plugin.getLogger().info("[CL DEBUG] Processing command for " + player.getName() + ": '" + command + "' -> '" + processedCommand + "'"); // New debug
            if (processedCommand.startsWith("/")) {
                processedCommand = processedCommand.substring(1);
            }
           // plugin.getLogger().info("[CL DEBUG] Dispatching command for " + player.getName() + ": /" + processedCommand); // New debug
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
        }
    }

    public void cancelCountdown(boolean finishedGracefully) {
        this.cancel(); // Stop this BukkitRunnable
        plugin.removePlayerActiveCountdown(player.getUniqueId(), group.getName());

        // Clean up display
        switch (group.getCountdownType()) {
            case EXP_BAR:
                if (plugin.getPlayerOriginalExp().containsKey(player.getUniqueId())) {
                    player.setExp(plugin.getPlayerOriginalExp().get(player.getUniqueId()));
                    plugin.getPlayerOriginalExp().remove(player.getUniqueId());
                }
                if (plugin.getPlayerOriginalLevel().containsKey(player.getUniqueId())) {
                    player.setLevel(plugin.getPlayerOriginalLevel().get(player.getUniqueId()));
                    plugin.getPlayerOriginalLevel().remove(player.getUniqueId());
                }
                break;
            case BOSS_BAR:
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                    plugin.getPlayerBossBars().remove(player.getUniqueId());
                }
                break;
            // Titles and Plain Text don't need persistent cleanup beyond stopping the task.
        }

        if (!finishedGracefully && player.isOnline()) {
            String exitMsg = plugin.formatMessage(player, group.getExitMessage(), group, timeLeft);
            if (!exitMsg.isEmpty()) player.sendMessage(exitMsg);
        }
    }
}
