package io.wax100.arenaCore;

import io.wax100.arenaCore.command.ArenaCommand;
import io.wax100.arenaCore.command.BetCommand;
import io.wax100.arenaCore.command.DoubleUpCommand;
import io.wax100.arenaCore.listener.ArenaBettingListener;
import io.wax100.arenaCore.listener.ArenaFightListener;
import io.wax100.arenaCore.listener.ArenaTeamAreaListener;
import io.wax100.arenaCore.listener.ArenaTerrainListener;
import io.wax100.arenaCore.listener.MobAreaProtectionListener;
import io.wax100.arenaCore.manager.AreaStore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.ArenaPresetStore;
import io.wax100.arenaCore.manager.BettingManager;
import io.wax100.arenaCore.manager.DoubleUpManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.manager.TerrainManager;
import io.wax100.arenaCore.storage.MemoryTerrainStorage;
import io.wax100.arenaCore.storage.RedisTerrainStorage;
import io.wax100.arenaCore.storage.TerrainStorageProvider;
import io.wax100.arenaCore.manager.JackpotManager;
import io.wax100.arenaCore.payout.PayoutDistributor;
import io.wax100.arenaCore.wincondition.LastTeamStandingCondition;
import io.wax100.arenaCore.wincondition.ManualDeclarationCondition;
import io.wax100.arenaCore.wincondition.ScoreCondition;
import io.wax100.arenaCore.wincondition.WinCondition;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ArenaCore プラグインのメインクラス。
 *
 * <p>CasinoCore のチップシステムを利用した闘技場ベッティングプラグイン。
 * 物理カーペット設置によるベット、WorldEdit エリア指定、
 * 設定可能な勝利条件と配当方式を提供する。
 *
 * @author wax100
 */
public final class ArenaCore extends JavaPlugin {

    private Economy economy;
    private ChipManager chipManager;
    private ArenaManager arenaManager;
    private BettingManager bettingManager;
    private RegionManager regionManager;
    private TerrainManager terrainManager;
    private ArenaPresetStore presetStore;
    private AreaStore areaStore;
    private PayoutDistributor payoutDistributor;
    private JackpotManager jackpotManager;
    private DoubleUpManager doubleUpManager;
    private TerrainStorageProvider terrainStorage;
    private WinCondition winCondition;

    // ── 分配デフォルト定数 ──
    private static final double DEFAULT_LOSER_SHARE = 0.01;
    private static final double DEFAULT_WINNER_SHARE = 0.10;
    private static final double DEFAULT_HOUSE_FEE = 0.05;
    private static final double DEFAULT_JACKPOT_THRESHOLD = 0.10;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // CasinoCore 取得
        Plugin casinoCorePlugin = getServer().getPluginManager().getPlugin("CasinoCore");
        if (!(casinoCorePlugin instanceof CasinoCore casinoCore)) {
            getLogger().severe("CasinoCore が見つかりません！無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        chipManager = casinoCore.getChipManager();

        // Vault 経済
        if (!setupEconomy()) {
            getLogger().severe("Vault の経済プラグインが見つかりません！無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 配当・ジャックポット初期化
        payoutDistributor = new PayoutDistributor();
        jackpotManager = new JackpotManager(getDataFolder(), getLogger());
        validateDistributionConfig();
        initWinCondition();

        // WorldEdit チェック
        boolean worldEditAvailable = getServer().getPluginManager().getPlugin("WorldEdit") != null;
        if (worldEditAvailable) {
            getLogger().info("WorldEdit が検出されました。エリア指定機能が有効です。");
        } else {
            getLogger().info("WorldEdit が見つかりません。手動チーム分けのみ使用可能です。");
        }

        // 地形復元ストレージの選択
        terrainStorage = createTerrainStorage();

        // マネージャ初期化
        regionManager = new RegionManager(worldEditAvailable);
        bettingManager = new BettingManager(this);
        terrainManager = new TerrainManager(this, terrainStorage);
        presetStore = new ArenaPresetStore(getDataFolder(), getLogger());
        areaStore = new AreaStore(getDataFolder(), getLogger());
        doubleUpManager = new DoubleUpManager(this);
        arenaManager = new ArenaManager(this, bettingManager, regionManager, terrainManager);

        // コマンド登録
        registerCommand("arena", new ArenaCommand(this));
        registerCommand("bet", new BetCommand(this));

        // DoubleUp コマンド登録
        PluginCommand doubleUpCmd = getCommand("doubleup");
        if (doubleUpCmd != null) {
            DoubleUpCommand handler = new DoubleUpCommand(this);
            doubleUpCmd.setExecutor(handler);
            doubleUpCmd.setTabCompleter(handler);
        }

        // リスナー登録
        getServer().getPluginManager().registerEvents(new ArenaBettingListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaFightListener(this), this);
        getServer().getPluginManager().registerEvents(new MobAreaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaTerrainListener(terrainManager), this);
        getServer().getPluginManager().registerEvents(new ArenaTeamAreaListener(this), this);

        // クラッシュ復旧チェック（全ワールドロード完了後に実行）
        getServer().getScheduler().runTaskLater(this, terrainManager::checkCrashRecovery, 1L);

        getLogger().info("ArenaCore が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (doubleUpManager != null) doubleUpManager.shutdown();
        if (arenaManager != null) arenaManager.shutdown();
        if (terrainStorage != null) terrainStorage.shutdown();
        getLogger().info("ArenaCore が無効化されました。");
    }

    /**
     * config に基づいて地形復元ストレージプロバイダを生成する。
     *
     * <p>{@code storage.type} が {@code "redis"} の場合、ChipLib の
     * {@code RedisConnectionManager} を取得して Redis ストレージを使用する。
     * 取得に失敗した場合はインメモリにフォールバックする。
     *
     * @return 選択された {@link TerrainStorageProvider}
     */
    private TerrainStorageProvider createTerrainStorage() {
        String storageType = getConfig().getString("storage.type", "yaml");
        if ("redis".equalsIgnoreCase(storageType)) {
            Plugin chipLibPlugin = getServer().getPluginManager().getPlugin("ChipLib");
            if (chipLibPlugin instanceof io.wax100.chipLib.ChipPlugin chipPlugin) {
                try {
                    var redisMgr = chipPlugin.getRedisConnectionManager();
                    if (redisMgr != null && redisMgr.isAvailable()) {
                        String prefix = getConfig().getString("storage.redis-prefix", "colosseum");
                        getLogger().info("地形復元ストレージ: Redis");
                        return new RedisTerrainStorage(redisMgr, prefix);
                    } else {
                        getLogger().warning("Redis 接続不可 — インメモリストレージにフォールバック");
                    }
                } catch (NoSuchMethodError e) {
                    // ChipLib が getRedisConnectionManager() を未実装の場合
                    getLogger().warning("ChipLib が RedisConnectionManager 未対応 — インメモリストレージにフォールバック");
                }
            } else {
                getLogger().warning("ChipLib が見つかりません — インメモリストレージにフォールバック");
            }
        }
        getLogger().info("地形復元ストレージ: インメモリ");
        return new MemoryTerrainStorage();
    }

    /**
     * 分配率の設定値をバリデーションする。
     *
     * <p>合計が 1.0 を超える場合はデフォルト値にフォールバックする。
     */
    private void validateDistributionConfig() {
        double loser = getConfig().getDouble("distribution.loser-fighter-share", DEFAULT_LOSER_SHARE);
        double winner = getConfig().getDouble("distribution.winner-fighter-share", DEFAULT_WINNER_SHARE);
        double house = getConfig().getDouble("distribution.house-fee", DEFAULT_HOUSE_FEE);
        double total = loser + winner + house;

        if (loser < 0 || winner < 0 || house < 0 || total > 1.0) {
            getLogger().warning("分配率の設定が不正です (合計: " + total + ")。デフォルト値を使用します。");
            getConfig().set("distribution.loser-fighter-share", DEFAULT_LOSER_SHARE);
            getConfig().set("distribution.winner-fighter-share", DEFAULT_WINNER_SHARE);
            getConfig().set("distribution.house-fee", DEFAULT_HOUSE_FEE);
            saveConfig();
        }

        // ジャックポット閾値のバリデーション
        double threshold = getConfig().getDouble("jackpot.trigger-threshold", DEFAULT_JACKPOT_THRESHOLD);
        if (threshold < 0.0 || threshold > 1.0) {
            getLogger().warning("jackpot.trigger-threshold が不正です (" + threshold + ")。デフォルト値 " + DEFAULT_JACKPOT_THRESHOLD + " を使用します。");
            getConfig().set("jackpot.trigger-threshold", DEFAULT_JACKPOT_THRESHOLD);
        }

        getLogger().info("配当方式: パリミュチュエル（天引き分配）");
        getLogger().info("  敗者還元: " + getConfig().getDouble("distribution.loser-fighter-share", DEFAULT_LOSER_SHARE)
                + " / 勝者還元: " + getConfig().getDouble("distribution.winner-fighter-share", DEFAULT_WINNER_SHARE)
                + " / 手数料: " + getConfig().getDouble("distribution.house-fee", DEFAULT_HOUSE_FEE));
    }

    /**
     * config.yml の win-condition に基づいて勝利条件を選択する。
     */
    private void initWinCondition() {
        String condition = getConfig().getString("win-condition", "last-team-standing");
        switch (condition) {
            case "manual":
                winCondition = new ManualDeclarationCondition();
                getLogger().info("勝利条件: 手動宣言");
                break;
            case "score":
                int target = getConfig().getInt("score-target", 0);
                winCondition = new ScoreCondition(target);
                getLogger().info("勝利条件: スコア制 (目標: " + (target > 0 ? target : "手動集計") + ")");
                break;
            default:
                winCondition = new LastTeamStandingCondition();
                getLogger().info("勝利条件: 全滅方式");
                break;
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    private <T extends CommandExecutor & TabCompleter>
    void registerCommand(String name, T handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("コマンド '" + name + "' が plugin.yml に定義されていません。");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    // ── Getters ──

    public Economy getEconomy() { return economy; }
    public ChipManager getChipManager() { return chipManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public BettingManager getBettingManager() { return bettingManager; }
    public RegionManager getRegionManager() { return regionManager; }
    public PayoutDistributor getPayoutDistributor() { return payoutDistributor; }
    public JackpotManager getJackpotManager() { return jackpotManager; }
    public WinCondition getWinCondition() { return winCondition; }
    public TerrainManager getTerrainManager() { return terrainManager; }
    public ArenaPresetStore getPresetStore() { return presetStore; }
    public AreaStore getAreaStore() { return areaStore; }
    public DoubleUpManager getDoubleUpManager() { return doubleUpManager; }
}
