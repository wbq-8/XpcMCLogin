package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XpcMCLogin extends JavaPlugin {

    private LoginManager loginManager;
    private FileConfiguration messages;
    private Map<String, FileConfiguration> languageFiles = new HashMap<>();
    private String currentLanguage = "en";

    @Override
    public void onEnable() {
        // 创建插件数据文件夹
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        // 创建本地化文件夹
        File localFolder = new File(getDataFolder(), "local");
        if (!localFolder.exists()) {
            localFolder.mkdir();
        }

        // 加载或创建语言文件
        loadLanguageFiles(localFolder);

        // 加载配置
        saveDefaultConfig();
        reloadConfig();

        // 初始化登录管理器
        loginManager = new LoginManager(this);

        // 注册命令
        getCommand("l").setExecutor(loginManager);
        getCommand("reg").setExecutor(loginManager);
        getCommand("l-to").setExecutor(loginManager);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(loginManager, this);

        // 加载已登录玩家数据
        loginManager.loadPlayerData();

        getLogger().info("XpcMCLogin has been enabled!");
    }

    @Override
    public void onDisable() {
        // 保存玩家数据
        loginManager.savePlayerData();

        getLogger().info("XpcMCLogin has been disabled!");
    }

    private void loadLanguageFiles(File localFolder) {
        String[] languages = {"en", "zh_cn", "zh_tw"};

        for (String lang : languages) {
            File langFile = new File(localFolder, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = getResource("local/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath());
                    } else {
                        getLogger().warning("Could not find language file: " + lang + ".yml in jar");
                    }
                } catch (IOException e) {
                    getLogger().warning("Failed to create language file: " + lang + ".yml");
                    e.printStackTrace();
                }
            }

            // 加载语言文件
            languageFiles.put(lang, YamlConfiguration.loadConfiguration(langFile));
        }

        // 设置当前语言
        currentLanguage = getConfig().getString("language", "en");
        messages = languageFiles.getOrDefault(currentLanguage, languageFiles.get("en"));
    }

    public String getMessage(String key) {
        return messages.getString(key, "Missing message: " + key);
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }
}