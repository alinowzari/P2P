// src/main/java/model/GameStatus.java
package model;

import config.StatusConfig;
import config.StatusConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds runtime game status (coins, per-level progress).
 * Does its own loading/saving via StatusConfigManager.
 */
public class GameStatus {

    /** Global wallet across the whole game (requested as static). */
    public static int totalCoinCount;

    /** Per-level coins and pass flags (keyed by levelName). */
    private final Map<String, Integer> coinsByLevel  = new HashMap<>();
    private final Map<String, Boolean> passedByLevel = new HashMap<>();

    /** Access to the config loader/saver. */
    private final StatusConfigManager cfgMgr;

    // ----- lifecycle ---------------------------------------------------

    public GameStatus() {
        this.cfgMgr = StatusConfigManager.getInstance();
        // Load once at construction
        StatusConfig cfg = cfgMgr.getStatus();
        applyConfig(cfg);
    }

    /** Replace in-memory state with what's in the current DTO. */
    private void applyConfig(StatusConfig cfg) {
        coinsByLevel.clear();
        passedByLevel.clear();
        totalCoinCount = Math.max(0, cfg.totalCoinCount());
        for (StatusConfig.LevelStatus ls : cfg.levels()) {
            coinsByLevel.put(ls.levelName(), Math.max(0, ls.coins()));
            passedByLevel.put(ls.levelName(), ls.passed());
        }
    }

    /** Build a DTO from the current in-memory state (for saving). */
    private StatusConfig toConfig() {
        List<StatusConfig.LevelStatus> levels = coinsByLevel.entrySet().stream()
                .map(e -> new StatusConfig.LevelStatus(
                        e.getKey(),
                        e.getValue(),
                        passedByLevel.getOrDefault(e.getKey(), false)))
                .toList();
        return new StatusConfig(totalCoinCount, levels);
    }

    /** Persist current state to gameStatus.json. */
    public void save() {
        cfgMgr.save(toConfig());
    }

    // ----- queries -----------------------------------------------------

    public int getTotalCoins() { return totalCoinCount; }
    public int getCoinsForLevel(String levelName) { return coinsByLevel.getOrDefault(levelName, 0); }
    public boolean isLevelPassed(String levelName) { return passedByLevel.getOrDefault(levelName, false); }
    public Map<String,Integer> coinsView() { return Map.copyOf(coinsByLevel); }
    public Map<String,Boolean> passedView() { return Map.copyOf(passedByLevel); }

    // ----- mutations ---------------------------------------------------

    public void setLevelPassed(String levelName, boolean passed) {
        passedByLevel.put(levelName, passed);
    }

    /** Adds coins to a level *and* to the global total. */
    public void addCoinsToLevel(String levelName, int coinsEarned) {
        int previousCoins = coinsByLevel.get(levelName);
        if(previousCoins < coinsEarned) {
            coinsByLevel.put(levelName, coinsEarned);
            totalCoinCount += (coinsEarned-previousCoins);
        }
    }

    /** Attempts to spend from the global wallet. */
    public boolean trySpend(int price) {
        if (price < 0 || totalCoinCount < price) {
            return false;
        }
        totalCoinCount -= price;
        return true;
    }

    // Optional helper: mark a win and bank coins, then save.
    public void commitWin(String levelName, int coinsEarned) {
        setLevelPassed(levelName, true);
        addCoinsToLevel(levelName, coinsEarned);
        save();
    }
    public int getTotalCoin() { return totalCoinCount; }
    public void setTotalCoin(int totalCoinCount) { this.totalCoinCount = totalCoinCount; }
}
