package io.wax100.chipLib;

import io.wax100.chipLib.command.ChipCommand;
import io.wax100.chipLib.command.RankingCommand;
import io.wax100.chipLib.ranking.RankingManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ChipLib プラグインのメインクラス。
 *
 * <p>Bukkit の {@link JavaPlugin} を継承し、チップ管理ライブラリをプラグインとして提供する。
 * 起動時に Vault 経済連携・{@link ChipManager} の初期化・{@code /chip} コマンドの登録を行う。
 *
 * <h2>依存プラグイン</h2>
 * <ul>
 *   <li><b>Vault</b> — 経済 (Economy) API の提供</li>
 * </ul>
 *
 * @author wax100
 * @see ChipManager
 * @see ChipCommand
 */
public final class ChipPlugin extends JavaPlugin implements Listener {

    /**
     * Vault 経済プラグインのインスタンス
     */
    private Economy economy;

    /**
     * チップ管理マネージャ
     */
    private ChipManager chipManager;

    /**
     * ランキング管理マネージャ
     */
    private RankingManager rankingManager;

    /**
     * チップ使用を許可されたプレイヤーのセット
     */
    private final Set<UUID> allowedPlayers = new HashSet<>();

    /**
     * チップ購入前の元のゲームモードを保持するマップ
     */
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();

    /**
     * プラグイン有効化時の初期化処理。
     *
     * <p>以下の順序で初期化を行う:
     * <ol>
     *   <li>デフォルト設定ファイルの保存</li>
     *   <li>Vault 経済プラグインのセットアップ（失敗時はプラグインを無効化）</li>
     *   <li>{@link ChipManager} の生成</li>
     *   <li>コマンド ({@code /chip}) の登録</li>
     * </ol>
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vaultの経済プラグインが見つかりません！プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        chipManager = new ChipManager(this);
        rankingManager = new RankingManager(this);

        ChipCommand chipCommand = new ChipCommand(this);
        PluginCommand cmd = getCommand("chip");
        if (cmd != null) {
            cmd.setExecutor(chipCommand);
            cmd.setTabCompleter(chipCommand);
        } else {
            getLogger().warning("コマンド 'chip' がplugin.ymlに定義されていません。");
        }

        RankingCommand rankingCommand = new RankingCommand(this);
        PluginCommand rankingCmd = getCommand("ranking");
        if (rankingCmd != null) {
            rankingCmd.setExecutor(rankingCommand);
            rankingCmd.setTabCompleter(rankingCommand);
        } else {
            getLogger().warning("コマンド 'ranking' がplugin.ymlに定義されていません。");
        }

        // イベントリスナー登録（PlayerQuitEvent でメモリリーク防止）
        getServer().getPluginManager().registerEvents(this, this);

        // ランキング自動保存タイマーの開始
        rankingManager.startAutoSave(this);

        getLogger().info("ChipLib が有効化されました！");
    }

    /**
     * プラグイン無効化時の終了処理。
     */
    @Override
    public void onDisable() {
        if (rankingManager != null) {
            rankingManager.stopAutoSave();
            rankingManager.saveData();
        }
        getLogger().info("ChipLib が無効化されました。");
    }

    /**
     * プレイヤー退出時に previousGameModes エントリを削除してメモリリークを防止する。
     *
     * @param event プレイヤー退出イベント
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        previousGameModes.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Vault経済プラグインをセットアップする。
     *
     * @return セットアップ成功時 {@code true}
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Vault 経済プラグインのインスタンスを取得する。
     *
     * @return Vault {@link Economy} インスタンス
     */
    public Economy getEconomy() {
        return economy;
    }

    /**
     * チップ管理マネージャを取得する。
     *
     * @return {@link ChipManager} インスタンス
     */
    public ChipManager getChipManager() {
        return chipManager;
    }

    /**
     * ランキング管理マネージャを取得する。
     *
     * @return {@link RankingManager} インスタンス
     */
    public RankingManager getRankingManager() {
        return rankingManager;
    }

    /**
     * プレイヤーにチップ使用を許可する。
     * CasinoCore や ArenaCore など外部プラグインから呼び出される。
     *
     * @param playerId プレイヤーの UUID
     */
    public void allowPlayer(UUID playerId) {
        allowedPlayers.add(playerId);
    }

    /**
     * プレイヤーのチップ使用許可を解除する。
     *
     * @param playerId プレイヤーの UUID
     */
    public void disallowPlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
    }

    /**
     * プレイヤーがチップ使用を許可されているかを返す。
     *
     * @param playerId プレイヤーの UUID
     * @return 許可されている場合 {@code true}
     */
    public boolean isAllowed(UUID playerId) {
        return allowedPlayers.contains(playerId);
    }

    /**
     * 全プレイヤーのチップ使用許可をクリアする。
     */
    public void clearAllAllowed() {
        allowedPlayers.clear();
    }

    // ── 購入リスナー ──

    private ChipPurchaseListener purchaseListener;

    /**
     * チップ購入時に呼び出されるリスナーを設定する。
     *
     * @param listener 購入リスナー（null で解除）
     */
    public void setPurchaseListener(ChipPurchaseListener listener) {
        this.purchaseListener = listener;
    }

    /**
     * 購入リスナーを取得する。
     *
     * @return 登録されたリスナー、未登録の場合は null
     */
    public ChipPurchaseListener getPurchaseListener() {
        return purchaseListener;
    }

    /**
     * プレイヤーのチップを全て換金し、Economy に入金する。
     * 外部プラグインから呼び出すための公開メソッド。
     *
     * @param player 対象プレイヤー
     * @return 換金された合計額。チップがなければ 0
     */
    public long cashoutPlayer(Player player) {
        long totalValue = chipManager.calculateTotalValue(player);
        if (totalValue == 0) return 0;
        chipManager.removeAllChips(player);
        economy.depositPlayer(player, totalValue);

        // 元のゲームモードに復元
        GameMode previous = previousGameModes.remove(player.getUniqueId());
        if (previous != null && player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(previous);
        }

        return totalValue;
    }

    /**
     * チップ購入前の元のゲームモードを保持するマップを取得する。
     *
     * @return previousGameModes マップ
     */
    public Map<UUID, GameMode> getPreviousGameModes() {
        return previousGameModes;
    }
}
