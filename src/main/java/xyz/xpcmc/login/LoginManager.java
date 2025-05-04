package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

public class LoginManager implements CommandExecutor, Listener {

    private final XpcMCLogin plugin;
    private final Set<String> loggedInPlayers = new HashSet<>();
    private final Map<String, Location> playerLocations = new HashMap<>();
    private final Map<String, String> playerPasswords = new HashMap<>();
    private FileConfiguration playerData;

    public LoginManager(XpcMCLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("command.player_only"));
            return true;
        }

        Player player = (Player) sender;
        String playerName = player.getName();

        switch (cmd.getName().toLowerCase()) {
            case "l":
                return handleLogin(player, args);
            case "reg":
                return handleRegister(player, args);
            case "l-to":
                return handleForceLogin(player, args);
            default:
                return false;
        }
    }

    private boolean handleLogin(Player player, String[] args) {
        String playerName = player.getName();

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
            plugin.removeBlindness(player);
            restorePlayerLocation(player);
            player.sendMessage(plugin.getMessage("login.success"));
            return true;
        } else {
            player.sendMessage(plugin.getMessage("login.wrong_password"));
            return true;
        }
    }

    private boolean handleRegister(Player player, String[] args) {
        String playerName = player.getName();

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
        plugin.removeBlindness(player);
        savePlayerData();
        player.sendMessage(plugin.getMessage("register.success"));
        return true;
    }

    private boolean handleForceLogin(Player player, String[] args) {
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
        plugin.removeBlindness(target);
        restorePlayerLocation(target);
        player.sendMessage(plugin.getMessage("lto.success_sender").replace("%player%", targetName));
        target.sendMessage(plugin.getMessage("lto.success_target").replace("%player%", player.getName()));
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        playerLocations.put(playerName, player.getLocation());

        if (!loggedInPlayers.contains(playerName)) {
            applyLoginRestrictions(player);
            sendLoginPrompt(player);
        }
    }

    private void sendLoginPrompt(Player player) {
        String playerName = player.getName();
        if (!playerPasswords.containsKey(playerName)) {
            player.sendMessage(plugin.getMessage("welcome.new_player"));
            player.sendMessage(plugin.getMessage("register.usage"));
        } else {
            player.sendMessage(plugin.getMessage("welcome.returning_player"));
            player.sendMessage(plugin.getMessage("login.usage"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        playerLocations.put(playerName, player.getLocation());
        loggedInPlayers.remove(playerName);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!loggedInPlayers.contains(player.getName())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().split(" ")[0].toLowerCase();

        if (!loggedInPlayers.contains(player.getName()) &&
                !command.equals("/l") && !command.equals("/reg")) {
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

    private void applyLoginRestrictions(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                Integer.MAX_VALUE,
                1,
                false,
                false
        ));

        if (plugin.getConfig().getBoolean("features.custom_initial_location.enabled", false)) {
            Location initialLoc = getInitialLocation();
            if (initialLoc != null) {
                player.teleport(initialLoc);
            }
        }
    }

    private void restorePlayerLocation(Player player) {
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
        ConfigurationSection locSection = plugin.getConfig()
                .getConfigurationSection("features.custom_initial_location.location");
        return getLocationFromConfig(locSection);
    }

    private Location getSpawnLocation() {
        ConfigurationSection locSection = plugin.getConfig()
                .getConfigurationSection("features.custom_spawn_location.location");
        return getLocationFromConfig(locSection);
    }

    private Location getLocationFromConfig(ConfigurationSection section) {
        if (section == null) return null;
        return new Location(
                Bukkit.getWorld(section.getString("world")),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public void loadPlayerData() {
        File dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        playerData = YamlConfiguration.loadConfiguration(dataFile);

        if (playerData.contains("passwords")) {
            ConfigurationSection passwords = playerData.getConfigurationSection("passwords");
            for (String name : passwords.getKeys(false)) {
                playerPasswords.put(name, passwords.getString(name));
            }
        }

        if (playerData.contains("locations")) {
            ConfigurationSection locations = playerData.getConfigurationSection("locations");
            for (String name : locations.getKeys(false)) {
                playerLocations.put(name, getLocationFromConfig(locations.getConfigurationSection(name)));
            }
        }
    }

    public void savePlayerData() {
        ConfigurationSection passwords = playerData.createSection("passwords");
        playerPasswords.forEach(passwords::set);

        ConfigurationSection locations = playerData.createSection("locations");
        playerLocations.forEach((name, loc) -> {
            ConfigurationSection locSection = locations.createSection(name);
            locSection.set("world", loc.getWorld().getName());
            locSection.set("x", loc.getX());
            locSection.set("y", loc.getY());
            locSection.set("z", loc.getZ());
            locSection.set("yaw", loc.getYaw());
            locSection.set("pitch", loc.getPitch());
        });

        try {
            playerData.save(new File(plugin.getDataFolder(), "playerdata.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data");
        }
    }
}