package io.wax100.casinoCore;

import io.wax100.bindingCurseLib.BindingCurseListener;
import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.command.CasinoCommand;
import io.wax100.casinoCore.listener.CasinoListener;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CasinoCore プラグインのメインクラス。
 *
 * <p>Bukkit の {@link JavaPlugin} を継承し、プラグインのライフサイクル管理を行う。
 * 起動時に Vault 経済連携・各マネージャの初期化・コマンドとリスナーの登録を行い、
 * 終了時にはカジノモードの強制終了とデータ保存を実施する。
 *
 * <h2>依存プラグイン</h2>
 * <ul>
 *   <li><b>Vault</b> — 経済 (Economy) API の提供</li>
 *   <li><b>BindingCurseLib</b> — 束縛の呪いによるアイテム所有者制御</li>
 *   <li><b>NBTAPI</b> — CanDestroy / CanPlaceOn 等の NBT 操作</li>
 * </ul>
 *
 * @author wax100
 * @see CasinoManager
 * @see ChipManager
 */
public final class CasinoCore extends JavaPlugin {

    /**
     * Vault 経済プラグインのインスタンス
     */
    private Economy economy;
    /**
     * チップ管理マネージャ
     */
    private ChipManager chipManager;
    /**
     * カジノ全体の状態管理マネージャ
     */
    private CasinoManager casinoManager;
    /**
     * 束縛の呪いアイテム所有者管理マネージャ
     */
    private BindingCurseManager bindingCurseManager;

    /**
     * プラグイン有効化時の初期化処理。
     *
     * <p>以下の順序で初期化を行う:
     * <ol>
     *   <li>デフォルト設定ファイルの保存</li>
     *   <li>Vault 経済プラグインのセットアップ（失敗時はプラグインを無効化）</li>
     *   <li>各マネージャ ({@link BindingCurseManager}, {@link ChipManager}, {@link CasinoManager}) の生成</li>
     *   <li>コマンド ({@code /casino}, {@code /chip}) の登録</li>
     *   <li>イベントリスナー ({@link CasinoListener}, {@link BindingCurseListener}) の登録</li>
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

        bindingCurseManager = new BindingCurseManager(this);

        // ChipLib から ChipManager を取得（NamespacedKey を統一するため）
        org.bukkit.plugin.Plugin chipLibPlugin = getServer().getPluginManager().getPlugin("ChipLib");
        if (chipLibPlugin instanceof io.wax100.chipLib.ChipPlugin chipPlugin) {
            chipManager = chipPlugin.getChipManager();
        } else {
            getLogger().severe("ChipLib が見つかりません！プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        casinoManager = new CasinoManager(this, bindingCurseManager);

        registerCommand("casino", new CasinoCommand(this));
        getServer().getPluginManager().registerEvents(new CasinoListener(this), this);
        getServer().getPluginManager().registerEvents(new BindingCurseListener(this, bindingCurseManager), this);

        // ChipLib に購入リスナーを登録（ランキング記録用）
        ((io.wax100.chipLib.ChipPlugin) chipLibPlugin).setPurchaseListener((playerId, totalCost) ->
                casinoManager.recordPurchase(playerId, totalCost));

        getLogger().info("CasinoCore が有効化されました！");
    }

    /**
     * プラグイン無効化時の終了処理。
     *
     * <p>カジノモードが稼働中の場合、全プレイヤーのチップを換金し、
     * ゲームモードを復元したうえでカジノモードを停止する。
     * 最後にデータファイルへの永続化を行う。
     */
    @Override
    public void onDisable() {
        if (casinoManager != null) {
            try {
                if (casinoManager.isCasinoActive()) {
                    casinoManager.cashoutAllPlayers();
                    casinoManager.restoreGameModes();
                    casinoManager.setCasinoActive(false);
                    casinoManager.clearAllSessionData();
                    getLogger().info("カジノモードを強制終了しました。");
                }
            } catch (Exception e) {
                getLogger().severe("カジノモード終了処理中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            } finally {
                casinoManager.saveData(false);
            }
        }
        getLogger().info("CasinoCore が無効化されました。");
    }

    /**
     * Vault経済プラグインをセットアップする
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
     * コマンドの Executor と TabCompleter を一括登録するヘルパー
     */
    private <T extends org.bukkit.command.CommandExecutor & org.bukkit.command.TabCompleter>
    void registerCommand(String name, T handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("コマンド '" + name + "' がplugin.ymlに定義されていません。");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
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
     * カジノ状態管理マネージャを取得する。
     *
     * @return {@link CasinoManager} インスタンス
     */
    public CasinoManager getCasinoManager() {
        return casinoManager;
    }
}
