package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Level;

public class XpcMCLogin extends JavaPlugin implements Listener {

    private FileConfiguration playersConfig;
    private File playersFile;
    private FileConfiguration config;
    private Set<String> loggedInPlayers = new HashSet<>();
    private HashMap<String, Location> lastLocations = new HashMap<>();
    private HashMap<String, Long> joinTimes = new HashMap<>();
    private HashMap<InetAddress, Integer> ipAccounts = new HashMap<>();
    private Location defaultSpawnLocation;
    private Location initialJoinLocation;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadConfig();
        loadPlayersData();
        loadIPData();
        loadLocations();

        getServer().getPluginManager().registerEvents(this, this);

        // 启动检查未登录玩家的任务
        new BukkitRunnable() {
            @Override
            public void run() {
                checkLoginTimeouts();
            }
        }.runTaskTimer(this, 20L * 60 * 2, 20L * 60 * 2); // 每2分钟检查一次

        getLogger().info("XpcMCLogin 已启用!");
    }

    @Override
    public void onDisable() {
        savePlayersData();
        saveIPData();
        getLogger().info("XpcMCLogin 已禁用!");
    }

    private void loadConfig() {
        config = getConfig();
        config.addDefault("loginTimeout", 120); // 2分钟
        config.addDefault("maxAccountsPerIP", 2);
        config.options().copyDefaults(true);
        saveConfig();
    }

    private void loadPlayersData() {
        playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                playersFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法创建 players.yml 文件", e);
            }
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }

    private void loadIPData() {
        File ipFile = new File(getDataFolder(), "ipdata.yml");
        if (ipFile.exists()) {
            FileConfiguration ipConfig = YamlConfiguration.loadConfiguration(ipFile);
            for (String ipStr : ipConfig.getKeys(false)) {
                try {
                    InetAddress ip = InetAddress.getByName(ipStr);
                    ipAccounts.put(ip, ipConfig.getInt(ipStr));
                } catch (Exception e) {
                    getLogger().warning("无法加载IP数据: " + ipStr);
                }
            }
        }
    }

    private void savePlayersData() {
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法保存 players.yml 文件", e);
        }
    }

    private void saveIPData() {
        File ipFile = new File(getDataFolder(), "ipdata.yml");
        FileConfiguration ipConfig = new YamlConfiguration();
        for (Map.Entry<InetAddress, Integer> entry : ipAccounts.entrySet()) {
            ipConfig.set(entry.getKey().getHostAddress(), entry.getValue());
        }
        try {
            ipConfig.save(ipFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法保存 ipdata.yml 文件", e);
        }
    }

    private void loadLocations() {
        if (config.contains("initialJoinLocation")) {
            initialJoinLocation = (Location) config.get("initialJoinLocation");
        } else {
            initialJoinLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (config.contains("defaultSpawnLocation")) {
            defaultSpawnLocation = (Location) config.get("defaultSpawnLocation");
        } else {
            defaultSpawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
    }

    private boolean isPlayerLoggedIn(Player player) {
        return loggedInPlayers.contains(player.getName());
    }

    private void loginPlayer(Player player, String password) {
        if (isPlayerLoggedIn(player)) {
            player.sendMessage("§c您已经登录了!");
            return;
        }

        String storedHash = playersConfig.getString(player.getName() + ".password");
        if (storedHash != null && PasswordHasher.verifyPassword(password, storedHash)) {
            loggedInPlayers.add(player.getName());

            Location lastLocation = (Location) playersConfig.get(player.getName() + ".lastLocation");
            if (lastLocation != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(lastLocation);
                    }
                }.runTask(this);
            }

            player.sendMessage("§a登录成功!");
        } else {
            player.sendMessage("§c密码错误!");
        }
    }

    private void registerPlayer(Player player, String password) {
        if (playersConfig.contains(player.getName())) {
            // 已注册，视为重置密码
            String newHash = PasswordHasher.hashPassword(password);
            playersConfig.set(player.getName() + ".password", newHash);
            savePlayersData();
            player.sendMessage("§a密码已重置!");
            return;
        }

        // 检查IP限制
        InetAddress ip = player.getAddress().getAddress();
        int accounts = ipAccounts.getOrDefault(ip, 0);
        int maxAccounts = config.getInt("maxAccountsPerIP", 2);

        if (accounts >= maxAccounts) {
            player.sendMessage("§c每个IP最多只能注册 " + maxAccounts + " 个账号!");
            return;
        }

        String hashedPassword = PasswordHasher.hashPassword(password);
        playersConfig.set(player.getName() + ".password", hashedPassword);
        playersConfig.set(player.getName() + ".lastLocation", player.getLocation());
        savePlayersData();

        ipAccounts.put(ip, accounts + 1);
        saveIPData();

        loggedInPlayers.add(player.getName());
        player.sendMessage("§a注册成功! 您现在已登录。");
    }

    private void checkLoginTimeouts() {
        long timeout = config.getLong("loginTimeout", 120) * 1000; // 转换为毫秒
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerLoggedIn(player)) {
                Long joinTime = joinTimes.get(player.getName());
                if (joinTime != null && (currentTime - joinTime) > timeout) {
                    player.kickPlayer("§c登录超时，请在" + (timeout/1000) + "秒内登录");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinTimes.put(player.getName(), System.currentTimeMillis());
        lastLocations.put(player.getName(), player.getLocation());

        if (!isPlayerLoggedIn(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.teleport(initialJoinLocation);
                }
            }.runTask(this);
        } else {
            Location lastLocation = (Location) playersConfig.get(player.getName() + ".lastLocation");
            if (lastLocation != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(lastLocation);
                    }
                }.runTask(this);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isPlayerLoggedIn(player)) {
            playersConfig.set(player.getName() + ".lastLocation", player.getLocation());
            savePlayersData();
        }
        loggedInPlayers.remove(player.getName());
        lastLocations.remove(player.getName());
        joinTimes.remove(player.getName());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerLoggedIn(player)) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        if (!isPlayerLoggedIn(player)) {
            if (!command.startsWith("/l ") && !command.startsWith("/login ") &&
                    !command.startsWith("/reg ") && !command.startsWith("/register ") &&
                    !command.startsWith("/xpcloginother ")) {
                event.setCancelled(true);
                player.sendMessage("§c请先登录!");
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (!isPlayerLoggedIn(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!isPlayerLoggedIn(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerLoggedIn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerLoggedIn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!isPlayerLoggedIn(player)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("l") || cmd.getName().equalsIgnoreCase("login")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家才能执行此命令!");
                return true;
            }

            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§c用法: /l <密码>");
                return true;
            }

            loginPlayer(player, args[0]);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("reg") || cmd.getName().equalsIgnoreCase("register")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家才能执行此命令!");
                return true;
            }

            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§c用法: /reg <密码>");
                return true;
            }

            registerPlayer(player, args[0]);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("xpcsetspawn")) {
            if (!sender.hasPermission("xpclogin.admin")) {
                sender.sendMessage("§c你没有权限执行此命令!");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家才能执行此命令!");
                return true;
            }

            Player player = (Player) sender;
            defaultSpawnLocation = player.getLocation();
            config.set("defaultSpawnLocation", defaultSpawnLocation);
            saveConfig();
            player.sendMessage("§a默认重生点已设置!");
            return true;
        } else if (cmd.getName().equalsIgnoreCase("xpcsetjoin")) {
            if (!sender.hasPermission("xpclogin.admin")) {
                sender.sendMessage("§c你没有权限执行此命令!");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家才能执行此命令!");
                return true;
            }

            Player player = (Player) sender;
            initialJoinLocation = player.getLocation();
            config.set("initialJoinLocation", initialJoinLocation);
            saveConfig();
            player.sendMessage("§a初始加入位置已设置!");
            return true;
        } else if (cmd.getName().equalsIgnoreCase("xpcloginother")) {
            if (args.length < 1) {
                sender.sendMessage("§c用法: /xpcloginother <玩家名>");
                return true;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§c玩家不在线!");
                return true;
            }

            loggedInPlayers.add(target.getName());
            sender.sendMessage("§a已强制登录玩家 " + targetName);
            target.sendMessage("§a你已被强制登录");
            return true;
        }

        return false;
    }
}