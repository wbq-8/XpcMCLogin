package xyz.xpcmc.login;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

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

            // 如果文件不存在，从JAR中复制
            if (!langFile.exists()) {
                try (InputStream in = getResource("local/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Created language file: " + langFile.getName());
                    } else {
                        getLogger().warning("Could not find language file in JAR: local/" + lang + ".yml");
                    }
                } catch (IOException e) {
                    getLogger().warning("Failed to create language file: " + langFile.getName());
                    e.printStackTrace();
                }
            }

            // 加载语言文件
            languageFiles.put(lang, YamlConfiguration.loadConfiguration(langFile));
        }

        // 设置当前语言
        currentLanguage = getConfig().getString("language", "en");
        messages = languageFiles.getOrDefault(currentLanguage, languageFiles.get("en"));

        if (messages == null) {
            getLogger().warning("Failed to load language: " + currentLanguage + ", falling back to en");
            messages = languageFiles.get("en");
        }
    }

    public String getMessage(String key) {
        return messages.getString(key, "Missing message: " + key);
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }
}