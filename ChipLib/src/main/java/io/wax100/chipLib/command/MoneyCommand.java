package io.wax100.chipLib.command;

import io.wax100.chipLib.BalanceDisplay;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.util.ChipMessages;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /money} コマンドハンドラ。
 *
 * <p>アクションバーの所持金表示をトグルで ON/OFF 切り替える。
 */
public class MoneyCommand implements CommandExecutor {

    private final ChipPlugin plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin ChipPlugin プラグインインスタンス
     */
    public MoneyCommand(ChipPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code /money} を実行するたびにアクションバー表示の ON/OFF を切り替える。
     * プレイヤー以外からの実行は拒否する。
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChipMessages.PLAYER_ONLY);
            return true;
        }

        BalanceDisplay display = plugin.getBalanceDisplay();
        boolean nowVisible = display.toggleDisplay(player.getUniqueId());

        if (nowVisible) {
            player.sendMessage(ChipMessages.PREFIX + ChatColor.GREEN + "所持金表示を ON にしました。");
        } else {
            player.sendMessage(ChipMessages.PREFIX + ChatColor.YELLOW + "所持金表示を OFF にしました。");
        }

        return true;
    }
}
