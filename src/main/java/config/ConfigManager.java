package config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Loads the main *gameConfig.json* (single level that will be played) and, if present,
 * attempts to load a *levels.json* array for the Level‑Select screen.
 *
 * If *levels.json* does not exist, we gracefully fall back to **“the single gameConfig
 * file is the only level available.”**  This removes the hard dependency that was
 * crashing your app.
 */
public final class ConfigManager {
    private static ConfigManager instance;

    private final GameConfig config;           // the level currently being played
    private final List<GameConfig> allLevels;  // every level available in the menu

    private ConfigManager() {
        try {
            // ---- 1. Mandatory: load the main level ---------------------------
// modern, wrapper-aware version
            Path gameConfigPath = Paths.get("gameConfig.json");
            List<GameConfig> mainLevels = ConfigLoader.loadLevels(gameConfigPath);
            this.config = mainLevels.getFirst();
            // ---- 2. Optional: load levels.json -------------------------------
            List<GameConfig> levels;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("levels.json")) {
                if (in != null) {
                    levels = ConfigLoader.loadLevels(in);
                } else {
                    Path levelsPath = Paths.get("levels.json");
                    if (Files.exists(levelsPath)) {
                        levels = ConfigLoader.loadLevels(levelsPath);
                    }
                    else {
                        // No levels.json?  Use the single gameConfig as the menu list.
                        levels = mainLevels;
                    }
                }
            }
            this.allLevels = levels;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public GameConfig getConfig() { return config; }

    public List<GameConfig> getAllLevels() { return allLevels; }
}