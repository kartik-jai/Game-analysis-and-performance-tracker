package utility;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * ConfigManager (Utility Layer)
 * -------------------------------------------------------------------------
 * Reads and saves application configuration from 'config.properties'.
 * Stores:
 *   1. HenrikDev Valorant API Key (HDEV-xxxx...)
 *   2. Riot Games Official API Key (RGAPI-xxxx...)
 *   3. Default Riot ID (Player Name + Tag) & Region (ap, na, eu, kr)
 *   4. SQLite Database URL
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private final Properties properties;

    public ConfigManager() {
        this.properties = new Properties();
        loadConfig();
    }

    public final void loadConfig() {
        Path path = Paths.get(CONFIG_FILE);
        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("[ConfigManager] Could not read config.properties: " + e.getMessage());
            }
        } else {
            // Set sensible defaults for a 3rd year B.Tech student in India (AP region)
            properties.setProperty("api.provider", "HENRIK_API");
            properties.setProperty("henrik.api.key", "HDEV-fdabd296-a67c-473d-9f7a-efc1f183ae40");
            properties.setProperty("riot.api.key", "");
            properties.setProperty("default.player.name", "Karthikkkk");
            properties.setProperty("default.player.tag", "1795");
            properties.setProperty("default.region", "ap");
            properties.setProperty("db.url", "jdbc:sqlite:valorant_tracker.db");
            properties.setProperty("local.log.path", "data/sample_valorant_logs.txt");
            saveConfig();
        }
    }

    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Valorant Game Analytics & Performance Tracker Configuration");
        } catch (IOException e) {
            System.err.println("[ConfigManager] Failed to save config.properties: " + e.getMessage());
        }
    }

    public String getHenrikApiKey() {
        String envKey = System.getenv("VALORANT_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return properties.getProperty("henrik.api.key", "HDEV-fdabd296-a67c-473d-9f7a-efc1f183ae40").trim();
    }

    public void setHenrikApiKey(String apiKey) {
        properties.setProperty("henrik.api.key", apiKey == null ? "" : apiKey.trim());
        saveConfig();
    }

    public String getRiotApiKey() {
        return properties.getProperty("riot.api.key", "").trim();
    }

    public void setRiotApiKey(String apiKey) {
        properties.setProperty("riot.api.key", apiKey == null ? "" : apiKey.trim());
        saveConfig();
    }

    public String getDefaultPlayerName() {
        return properties.getProperty("default.player.name", "Karthikkkk");
    }

    public String getDefaultPlayerTag() {
        return properties.getProperty("default.player.tag", "1795");
    }

    public String getDefaultRegion() {
        return properties.getProperty("default.region", "ap");
    }

    public void setDefaultPlayer(String name, String tag, String region) {
        properties.setProperty("default.player.name", name);
        properties.setProperty("default.player.tag", tag);
        properties.setProperty("default.region", region);
        saveConfig();
    }

    public String getDatabaseUrl() {
        return properties.getProperty("db.url", "jdbc:sqlite:valorant_tracker.db");
    }

    public String getLocalLogPath() {
        return properties.getProperty("local.log.path", "data/sample_valorant_logs.txt");
    }
}
