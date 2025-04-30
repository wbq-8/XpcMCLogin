package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        try {
            // 初始化目录和配置文件
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            loadConfig();
            loadPlayersData();
            loadIPData();
            loadLocations();

            // 注册事件监听器
            getServer().getPluginManager().registerEvents(this, this);

            // 启动登录超时检查任务
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkLoginTimeouts();
                }
            }.runTaskTimer(this, 20L * 30, 20L * 30); // 每30秒检查一次

            // 调试信息
            getLogger().info("插件已启用！当前已登录玩家: " + loggedInPlayers);
            getLogger().info("初始位置: " + initialJoinLocation);
            getLogger().info("重生点: " + defaultSpawnLocation);

        } catch (Exception e) {
            getLogger().severe("启动失败: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        savePlayersData();
        saveIPData();
        getLogger().info("插件已禁用，数据已保存");
    }

    // 配置加载方法
    private void loadConfig() {
        config = getConfig();
        config.addDefault("loginTimeout", 120);
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

        // 加载时同步已注册玩家的登录状态
        for (String playerName : playersConfig.getKeys(false)) {
            loggedInPlayers.add(playerName);
        }
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

    private void loadLocations() {
        // 加载初始位置
        initialJoinLocation = getLocationFromConfig(config.getConfigurationSection("initialJoinLocation"));
        if (initialJoinLocation == null) {
            initialJoinLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
            config.set("initialJoinLocation", serializeLocation(initialJoinLocation));
            saveConfig();
            getLogger().info("已创建默认初始位置");
        }

        // 加载重生点
        defaultSpawnLocation = getLocationFromConfig(config.getConfigurationSection("defaultSpawnLocation"));
        if (defaultSpawnLocation == null) {
            defaultSpawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
            config.set("defaultSpawnLocation", serializeLocation(defaultSpawnLocation));
            saveConfig();
            getLogger().info("已创建默认重生点");
        }
    }

    // 位置序列化工具方法
    private Location getLocationFromConfig(ConfigurationSection section) {
        if (section == null) return null;

        try {
            World world = Bukkit.getWorld(section.getString("world"));
            if (world == null) return null;

            return new Location(
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch")
            );
        } catch (Exception e) {
            getLogger().warning("加载位置配置出错: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> serializeLocation(Location loc) {
        Map<String, Object> serialized = new HashMap<>();
        if (loc == null || loc.getWorld() == null) return serialized;

        serialized.put("world", loc.getWorld().getName());
        serialized.put("x", loc.getX());
        serialized.put("y", loc.getY());
        serialized.put("z", loc.getZ());
        serialized.put("yaw", loc.getYaw());
        serialized.put("pitch", loc.getPitch());
        return serialized;
    }

    // 数据保存方法
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

    // 登录状态管理
    private boolean isPlayerLoggedIn(Player player) {
        boolean inMemory = loggedInPlayers.contains(player.getName());
        boolean inStorage = playersConfig.contains(player.getName());

        // 自动修复不一致状态
        if (inMemory && !inStorage) {
            loggedInPlayers.remove(player.getName());
            return false;
        }
        if (!inMemory && inStorage) {
            loggedInPlayers.add(player.getName());
        }

        return loggedInPlayers.contains(player.getName());
    }

    // 登录/注册核心方法
    private void loginPlayer(Player player, String password) {
        if (isPlayerLoggedIn(player)) {
            player.sendMessage("§a您已经处于登录状态");
            return;
        }

        String storedHash = playersConfig.getString(player.getName() + ".password");
        if (storedHash == null) {
            player.sendMessage("§c该账号未注册，请先使用/reg注册");
            return;
        }

        if (PasswordHasher.verifyPassword(password, storedHash)) {
            loggedInPlayers.add(player.getName());

            // 更新最后位置
            playersConfig.createSection(player.getName() + ".lastLocation",
                    serializeLocation(player.getLocation()));
            savePlayersData();

            // 传送至最后记录位置
            Location lastLocation = getLocationFromConfig(
                    playersConfig.getConfigurationSection(player.getName() + ".lastLocation"));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (lastLocation != null) {
                        player.teleport(lastLocation);
                    }
                    player.sendMessage("§a登录成功！");
                    getLogger().info(player.getName() + " 登录成功");
                }
            }.runTask(this);
        } else {
            player.sendMessage("§c密码错误！");
        }
    }

    private void registerPlayer(Player player, String password) {
        // 密码长度检查
        if (password.length() < 6) {
            player.sendMessage("§c密码长度至少需要6位");
            return;
        }

        boolean isRegistered = playersConfig.contains(player.getName());

        if (isRegistered) {
            // 密码重置流程
            String newHash = PasswordHasher.hashPassword(password);
            playersConfig.set(player.getName() + ".password", newHash);
            savePlayersData();

            loggedInPlayers.add(player.getName());
            player.sendMessage("§a密码已重置！您已自动登录");
            getLogger().info(player.getName() + " 重置了密码");
            return;
        }

        // IP限制检查
        InetAddress ip = player.getAddress().getAddress();
        int accounts = ipAccounts.getOrDefault(ip, 0);
        int maxAccounts = config.getInt("maxAccountsPerIP", 2);

        if (accounts >= maxAccounts) {
            player.sendMessage("§c每个IP最多只能注册 " + maxAccounts + " 个账号!");
            return;
        }

        // 新注册流程
        String hashedPassword = PasswordHasher.hashPassword(password);
        playersConfig.set(player.getName() + ".password", hashedPassword);
        playersConfig.createSection(player.getName() + ".lastLocation",
                serializeLocation(player.getLocation()));
        savePlayersData();

        ipAccounts.put(ip, accounts + 1);
        saveIPData();

        loggedInPlayers.add(player.getName());
        player.sendMessage("§a注册成功！您已自动登录");

        // 立即解除限制
        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawn = defaultSpawnLocation != null ?
                        defaultSpawnLocation :
                        Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(spawn);
            }
        }.runTask(this);

        getLogger().info(player.getName() + " 注册了新账号");
    }

    // 定时任务
    private void checkLoginTimeouts() {
        long timeout = config.getLong("loginTimeout", 120) * 1000;
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerLoggedIn(player)) {
                Long joinTime = joinTimes.get(player.getName());
                if (joinTime != null && (currentTime - joinTime) > timeout) {
                    player.kickPlayer("§c登录超时，请及时登录");
                    getLogger().info("踢出未登录玩家: " + player.getName());
                }
            }
        }
    }

    // 事件处理
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinTimes.put(player.getName(), System.currentTimeMillis());
        lastLocations.put(player.getName(), player.getLocation());

        // 状态同步
        boolean shouldBeLoggedIn = playersConfig.contains(player.getName());
        if (shouldBeLoggedIn) {
            loggedInPlayers.add(player.getName());
        } else {
            loggedInPlayers.remove(player.getName());
        }

        if (!isPlayerLoggedIn(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.teleport(initialJoinLocation != null ?
                            initialJoinLocation :
                            Bukkit.getWorlds().get(0).getSpawnLocation());
                    player.sendMessage("§e请使用 /l <密码> 登录或 /reg <密码> 注册");
                }
            }.runTask(this);
        } else {
            Location lastLocation = getLocationFromConfig(
                    playersConfig.getConfigurationSection(player.getName() + ".lastLocation"));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (lastLocation != null) {
                        player.teleport(lastLocation);
                    }
                    player.sendMessage("§a欢迎回来！");
                }
            }.runTask(this);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isPlayerLoggedIn(player)) {
            playersConfig.createSection(player.getName() + ".lastLocation",
                    serializeLocation(player.getLocation()));
            savePlayersData();
        }
        loggedInPlayers.remove(player.getName());
        lastLocations.remove(player.getName());
        joinTimes.remove(player.getName());
    }

    // 限制未登录玩家的行为
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerLoggedIn(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        if (!isPlayerLoggedIn(player)) {
            if (!command.startsWith("/l ") &&
                    !command.startsWith("/login ") &&
                    !command.startsWith("/reg ") &&
                    !command.startsWith("/register ") &&
                    !command.startsWith("/xpcloginother ")) {
                event.setCancelled(true);
                player.sendMessage("§c请先登录！可用命令: /l, /reg");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isPlayerLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
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
        if (!isPlayerLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isPlayerLoggedIn(event.getPlayer())) {
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

    // 命令处理
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能执行此命令");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("l") || cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) {
                player.sendMessage("§c用法: /l <密码>");
                return true;
            }
            loginPlayer(player, args[0]);
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("reg") || cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 1) {
                player.sendMessage("§c用法: /reg <密码>");
                return true;
            }
            registerPlayer(player, args[0]);
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("xpcsetspawn")) {
            if (!player.hasPermission("xpclogin.admin")) {
                player.sendMessage("§c你没有权限执行此命令");
                return true;
            }

            defaultSpawnLocation = player.getLocation();
            config.set("defaultSpawnLocation", serializeLocation(defaultSpawnLocation));
            saveConfig();
            player.sendMessage("§a默认重生点已设置！");
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("xpcsetjoin")) {
            if (!player.hasPermission("xpclogin.admin")) {
                player.sendMessage("§c你没有权限执行此命令");
                return true;
            }

            initialJoinLocation = player.getLocation();
            config.set("initialJoinLocation", serializeLocation(initialJoinLocation));
            saveConfig();
            player.sendMessage("§a初始加入位置已设置！");
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("xpcloginother")) {
            if (args.length < 1) {
                player.sendMessage("§c用法: /xpcloginother <玩家名>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§c玩家不在线或不存在");
                return true;
            }

            loggedInPlayers.add(target.getName());
            player.sendMessage("§a已强制登录玩家 " + target.getName());
            target.sendMessage("§a你已被管理员强制登录");
            return true;
        }

        return false;
    }
}