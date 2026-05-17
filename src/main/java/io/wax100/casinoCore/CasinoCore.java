package io.wax100.casinoCore;

import io.wax100.casinoCore.command.CasinoCommand;
import io.wax100.casinoCore.command.ChipCommand;
import io.wax100.casinoCore.listener.CasinoListener;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.casinoCore.manager.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class CasinoCore extends JavaPlugin {

    private Economy economy;
    private ChipManager chipManager;
    private CasinoManager casinoManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vaultの経済プラグインが見つかりません！プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        chipManager = new ChipManager(this);
        casinoManager = new CasinoManager(this);

        registerCommand("casino", new CasinoCommand(this));
        registerCommand("chip", new ChipCommand(this));
        getServer().getPluginManager().registerEvents(new CasinoListener(this), this);

        getLogger().info("CasinoCore が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (casinoManager != null) {
            if (casinoManager.isCasinoActive()) {
                casinoManager.cashoutAllPlayers();
                casinoManager.restoreGameModes();
                casinoManager.setCasinoActive(false);
                casinoManager.clearAllSessionData();
                getLogger().info("カジノモードを強制終了しました。");
            }
            casinoManager.saveData();
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

    public Economy getEconomy() {
        return economy;
    }

    public ChipManager getChipManager() {
        return chipManager;
    }

    public CasinoManager getCasinoManager() {
        return casinoManager;
    }
}
