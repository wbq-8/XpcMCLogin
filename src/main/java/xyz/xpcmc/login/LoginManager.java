package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoginManager implements CommandExecutor, Listener {

    private final XpcMCLogin plugin;
    private final Set<String> loggedInPlayers = new HashSet<>();
    private final Map<String, Location> playerLocations = new HashMap<>();
    private final Map<String, String> playerPasswords = new HashMap<>();
    private FileConfiguration playerData;

    public LoginManager(XpcMCLogin plugin) {
        this.plugin = plugin;
    }

    // 命令处理
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("command.player_only"));
            return true;
        }

        Player player = (Player) sender;
        String playerName = player.getName();

        if (cmd.getName().equalsIgnoreCase("l")) {
            // 登录命令
            if (args.length != 1) {
                player.sendMessage(plugin.getMessage("login.usage"));
                return false;
            }

            if (loggedInPlayers.contains(playerName)) {
                player.sendMessage(plugin.getMessage("login.already_logged_in"));
                return true;
            }

            if (!playerPasswords.containsKey(playerName)) {
                player.sendMessage(plugin.getMessage("login.not_registered"));
                return true;
            }

            if (playerPasswords.get(playerName).equals(args[0])) {
                loggedInPlayers.add(playerName);
                restorePlayerLocation(player);
                player.sendMessage(plugin.getMessage("login.success"));
                return true;
            } else {
                player.sendMessage(plugin.getMessage("login.wrong_password"));
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("reg")) {
            // 注册命令
            if (args.length != 2) {
                player.sendMessage(plugin.getMessage("register.usage"));
                return false;
            }

            if (playerPasswords.containsKey(playerName)) {
                player.sendMessage(plugin.getMessage("register.already_registered"));
                return true;
            }

            if (!args[0].equals(args[1])) {
                player.sendMessage(plugin.getMessage("register.password_mismatch"));
                return true;
            }

            playerPasswords.put(playerName, args[0]);
            loggedInPlayers.add(playerName);
            savePlayerData();
            player.sendMessage(plugin.getMessage("register.success"));
            return true;
        } else if (cmd.getName().equalsIgnoreCase("l-to")) {
            // 强制登录命令
            if (args.length != 1) {
                player.sendMessage(plugin.getMessage("lto.usage"));
                return false;
            }

            if (!plugin.getConfig().getBoolean("features.force_login", false)) {
                player.sendMessage(plugin.getMessage("lto.feature_disabled"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(plugin.getMessage("lto.player_not_found"));
                return true;
            }

            String targetName = target.getName();
            if (loggedInPlayers.contains(targetName)) {
                player.sendMessage(plugin.getMessage("lto.already_logged_in"));
                return true;
            }

            loggedInPlayers.add(targetName);
            restorePlayerLocation(target);
            player.sendMessage(plugin.getMessage("lto.success_sender").replace("%player%", targetName));
            target.sendMessage(plugin.getMessage("lto.success_target").replace("%player%", playerName));
            return true;
        }

        return false;
    }

    // 事件处理
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // 保存玩家位置
        playerLocations.put(playerName, player.getLocation());

        // 如果未登录，应用限制
        if (!loggedInPlayers.contains(playerName)) {
            applyLoginRestrictions(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // 保存玩家位置
        playerLocations.put(playerName, player.getLocation());

        // 从已登录列表中移除
        loggedInPlayers.remove(playerName);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!loggedInPlayers.contains(player.getName())) {
            // 取消移动
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase().split(" ")[0];

        if (!loggedInPlayers.contains(player.getName()) && !command.equals("/l") && !command.equals("/reg")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("restrictions.command_blocked"));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (!loggedInPlayers.contains(player.getName())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("restrictions.inventory_blocked"));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!loggedInPlayers.contains(player.getName())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("restrictions.inventory_blocked"));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!loggedInPlayers.contains(player.getName())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("restrictions.block_interaction_blocked"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!loggedInPlayers.contains(player.getName())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("restrictions.block_interaction_blocked"));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!loggedInPlayers.contains(player.getName())) {
                event.setCancelled(true);
            }
        }
    }

    // 辅助方法
    private void applyLoginRestrictions(Player player) {
        // 给予失明效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));

        // 检查是否有初始位置设置
        if (plugin.getConfig().getBoolean("features.custom_initial_location.enabled", false)) {
            Location initialLoc = getInitialLocation();
            if (initialLoc != null) {
                player.teleport(initialLoc);
            }
        }
    }

    private void restorePlayerLocation(Player player) {
        // 移除限制效果
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // 恢复位置
        String playerName = player.getName();
        if (playerLocations.containsKey(playerName)) {
            player.teleport(playerLocations.get(playerName));
        } else if (plugin.getConfig().getBoolean("features.custom_spawn_location.enabled", false)) {
            Location spawnLoc = getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
            }
        }
    }

    private Location getInitialLocation() {
        ConfigurationSection locSection = plugin.getConfig().getConfigurationSection("features.custom_initial_location.location");
        if (locSection != null) {
            return new Location(
                    Bukkit.getWorld(locSection.getString("world")),
                    locSection.getDouble("x"),
                    locSection.getDouble("y"),
                    locSection.getDouble("z"),
                    (float) locSection.getDouble("yaw"),
                    (float) locSection.getDouble("pitch")
            );
        }
        return null;
    }

    private Location getSpawnLocation() {
        ConfigurationSection locSection = plugin.getConfig().getConfigurationSection("features.custom_spawn_location.location");
        if (locSection != null) {
            return new Location(
                    Bukkit.getWorld(locSection.getString("world")),
                    locSection.getDouble("x"),
                    locSection.getDouble("y"),
                    locSection.getDouble("z"),
                    (float) locSection.getDouble("yaw"),
                    (float) locSection.getDouble("pitch")
            );
        }
        return null;
    }

    // 数据管理
    public void loadPlayerData() {
        File dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create playerdata.yml");
                e.printStackTrace();
            }
        }

        playerData = YamlConfiguration.loadConfiguration(dataFile);
        if (playerData.contains("passwords")) {
            ConfigurationSection passwordsSection = playerData.getConfigurationSection("passwords");
            for (String playerName : passwordsSection.getKeys(false)) {
                playerPasswords.put(playerName, passwordsSection.getString(playerName));
            }
        }

        if (playerData.contains("locations")) {
            ConfigurationSection locationsSection = playerData.getConfigurationSection("locations");
            for (String playerName : locationsSection.getKeys(false)) {
                ConfigurationSection locSection = locationsSection.getConfigurationSection(playerName);
                playerLocations.put(playerName, new Location(
                        Bukkit.getWorld(locSection.getString("world")),
                        locSection.getDouble("x"),
                        locSection.getDouble("y"),
                        locSection.getDouble("z"),
                        (float) locSection.getDouble("yaw"),
                        (float) locSection.getDouble("pitch")
                ));
            }
        }
    }

    public void savePlayerData() {
        // 保存密码
        ConfigurationSection passwordsSection = playerData.createSection("passwords");
        for (Map.Entry<String, String> entry : playerPasswords.entrySet()) {
            passwordsSection.set(entry.getKey(), entry.getValue());
        }

        // 保存位置
        ConfigurationSection locationsSection = playerData.createSection("locations");
        for (Map.Entry<String, Location> entry : playerLocations.entrySet()) {
            ConfigurationSection locSection = locationsSection.createSection(entry.getKey());
            Location loc = entry.getValue();
            locSection.set("world", loc.getWorld().getName());
            locSection.set("x", loc.getX());
            locSection.set("y", loc.getY());
            locSection.set("z", loc.getZ());
            locSection.set("yaw", loc.getYaw());
            locSection.set("pitch", loc.getPitch());
        }

        try {
            playerData.save(new File(plugin.getDataFolder(), "playerdata.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerdata.yml");
            e.printStackTrace();
        }
    }
}