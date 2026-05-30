package io.wax100.arenaCore;

import io.wax100.arenaCore.command.ArenaCommand;
import io.wax100.arenaCore.command.BetCommand;
import io.wax100.arenaCore.listener.ArenaBettingListener;
import io.wax100.arenaCore.listener.ArenaFightListener;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.BettingManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.payout.FixedOddsPayout;
import io.wax100.arenaCore.payout.PariMutuelPayout;
import io.wax100.arenaCore.payout.PayoutStrategy;
import io.wax100.arenaCore.payout.SimpleRedistributionPayout;
import io.wax100.arenaCore.wincondition.LastTeamStandingCondition;
import io.wax100.arenaCore.wincondition.ManualDeclarationCondition;
import io.wax100.arenaCore.wincondition.ScoreCondition;
import io.wax100.arenaCore.wincondition.WinCondition;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ArenaCore プラグインのメインクラス。
 *
 * <p>CasinoCore のチップシステムを利用した闘技場ベッティングプラグイン。
 * 物理カーペット設置による賭け、WorldEdit エリア指定、
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
    private PayoutStrategy payoutStrategy;
    private WinCondition winCondition;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // CasinoCore 取得
        CasinoCore casinoCore = (CasinoCore) getServer().getPluginManager().getPlugin("CasinoCore");
        if (casinoCore == null) {
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

        // Strategy 初期化
        initPayoutStrategy();
        initWinCondition();

        // WorldEdit チェック
        boolean worldEditAvailable = getServer().getPluginManager().getPlugin("WorldEdit") != null;
        if (worldEditAvailable) {
            getLogger().info("WorldEdit が検出されました。エリア指定機能が有効です。");
        } else {
            getLogger().info("WorldEdit が見つかりません。手動チーム分けのみ使用可能です。");
        }

        // マネージャ初期化
        regionManager = new RegionManager(worldEditAvailable);
        bettingManager = new BettingManager(this);
        arenaManager = new ArenaManager(this, bettingManager, regionManager);

        // コマンド登録
        registerCommand("arena", new ArenaCommand(this));
        registerCommand("bet", new BetCommand(this));

        // リスナー登録
        getServer().getPluginManager().registerEvents(new ArenaBettingListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaFightListener(this), this);

        getLogger().info("ArenaCore が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.shutdown();
        getLogger().info("ArenaCore が無効化されました。");
    }

    /**
     * config.yml の payout-method に基づいて配当方式を選択する。
     */
    private void initPayoutStrategy() {
        String method = getConfig().getString("payout-method", "pari-mutuel");
        switch (method) {
            case "fixed-odds":
                payoutStrategy = new FixedOddsPayout();
                getLogger().info("配当方式: 固定オッズ");
                break;
            case "simple":
                payoutStrategy = new SimpleRedistributionPayout();
                getLogger().info("配当方式: 単純再分配");
                break;
            default:
                payoutStrategy = new PariMutuelPayout();
                getLogger().info("配当方式: パリミュチュエル");
                break;
        }
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
        return economy != null;
    }

    private <T extends org.bukkit.command.CommandExecutor & org.bukkit.command.TabCompleter>
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
    public PayoutStrategy getPayoutStrategy() { return payoutStrategy; }
    public WinCondition getWinCondition() { return winCondition; }
}
