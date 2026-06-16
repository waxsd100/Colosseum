package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena config} — セッション固有のゲームルール設定を表示・変更する。
 */
public class ConfigSubCommand implements SubCommand {

    private static final List<String> CONFIG_KEYS = List.of(
            "entry-fee", "win-condition", "score-target", "fighter-guarantee");
    private static final List<String> WIN_CONDITIONS = List.of(
            "last-team-standing", "manual", "score");

    private final ArenaCore plugin;

    public ConfigSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        ArenaConfig config = session.getArenaConfig();
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "設定が初期化されていません。");
            return;
        }

        // 引数なし: 設定一覧表示
        if (args.length == 0) {
            showConfig(sender, config);
            return;
        }

        // セットアップ中のみ変更可能
        if (session.getState() != io.wax100.arenaCore.model.ArenaState.SETUP) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_SETUP_ONLY);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "使い方: /arena config <設定名> <値>");
            return;
        }

        String key = args[0].toLowerCase();
        String value = args[1];

        switch (key) {
            case "entry-fee" -> {
                try {
                    long fee = Long.parseLong(value);
                    config.setEntryFee(fee);
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                            + "参加費を " + ChipManager.formatAmount(config.getEntryFee()) + " E に設定しました。");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "数値を指定してください。");
                }
            }
            case "win-condition" -> {
                if (config.setWinCondition(value)) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                            + "勝利条件を " + config.getWinConditionDisplayName() + " に設定しました。");
                } else {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "無効な勝利条件です。選択肢: last-team-standing, manual, score");
                }
            }
            case "score-target" -> {
                try {
                    int target = Integer.parseInt(value);
                    config.setScoreTarget(target);
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                            + "スコア目標を " + config.getScoreTarget() + " に設定しました。");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "数値を指定してください。");
                }
            }
            case "fighter-guarantee" -> {
                try {
                    long guarantee = Long.parseLong(value);
                    config.setFighterGuarantee(guarantee);
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                            + "闘技者保証金を " + ChipManager.formatAmount(config.getFighterGuarantee()) + " E に設定しました。");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "数値を指定してください。");
                }
            }
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "不明な設定: " + key + "。選択肢: " + String.join(", ", CONFIG_KEYS));
        }
    }

    private void showConfig(CommandSender sender, ArenaConfig config) {
        sender.sendMessage(ArenaMessages.SEPARATOR);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "⚙ 闘技場設定");
        sender.sendMessage("");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "参加費: " + ChatColor.WHITE
                + ChipManager.formatAmount(config.getEntryFee()) + " E");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "勝利条件: " + ChatColor.WHITE
                + config.getWinConditionDisplayName());
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "スコア目標: " + ChatColor.WHITE
                + config.getScoreTarget());
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "闘技者保証金: " + ChatColor.WHITE
                + ChipManager.formatAmount(config.getFighterGuarantee()) + " E");
        sender.sendMessage("");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY
                + "変更: /arena config <設定名> <値>");
        sender.sendMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(CONFIG_KEYS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("win-condition")) {
            return CommandHelper.filterStartsWith(WIN_CONDITIONS, args[1]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena config [<設定名> <値>]";
    }
}
