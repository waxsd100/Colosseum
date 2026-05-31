package io.wax100.chipLib;

import io.wax100.chipLib.command.ChipCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
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
public final class ChipPlugin extends JavaPlugin {

    /**
     * Vault 経済プラグインのインスタンス
     */
    private Economy economy;

    /**
     * チップ管理マネージャ
     */
    private ChipManager chipManager;

    /**
     * チップ使用を許可されたプレイヤーのセット
     */
    private final Set<UUID> allowedPlayers = new HashSet<>();

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

        ChipCommand chipCommand = new ChipCommand(this);
        PluginCommand cmd = getCommand("chip");
        if (cmd != null) {
            cmd.setExecutor(chipCommand);
            cmd.setTabCompleter(chipCommand);
        } else {
            getLogger().warning("コマンド 'chip' がplugin.ymlに定義されていません。");
        }

        getLogger().info("ChipLib が有効化されました！");
    }

    /**
     * プラグイン無効化時の終了処理。
     */
    @Override
    public void onDisable() {
        getLogger().info("ChipLib が無効化されました。");
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
}
