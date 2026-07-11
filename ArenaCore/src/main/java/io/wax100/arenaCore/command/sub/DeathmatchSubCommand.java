package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.DeathmatchChallenge;
import io.wax100.arenaCore.model.MatchMode;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena deathmatch} — デスマッチ提案・投票を処理するサブコマンド。
 *
 * <p>サブサブコマンド:
 * <ul>
 *   <li>{@code /arena deathmatch <金額>} — デスマッチを提案（投票開始）</li>
 *   <li>{@code /arena deathmatch yes} — 賛成票を投じる</li>
 *   <li>{@code /arena deathmatch no} — 反対票を投じる</li>
 *   <li>{@code /arena deathmatch cancel} — 提案を取消</li>
 *   <li>{@code /arena deathmatch info} — 投票状況を表示</li>
 * </ul>
 */
public class DeathmatchSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public DeathmatchSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "yes" -> handleVote(sender, manager, true);
            case "no" -> handleVote(sender, manager, false);
            case "cancel" -> handleCancel(sender, manager, session);
            case "info" -> handleInfo(sender, manager, session);
            default -> handlePropose(sender, manager, session, args[0]);
        }
    }

    /**
     * デスマッチ提案を処理する。
     */
    private void handlePropose(CommandSender sender, ArenaManager manager,
                               ArenaSession session, String amountStr) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        // 状態チェック: BETTING または BLIND のみ
        ArenaState state = session.getState();
        if (state != ArenaState.BETTING && state != ArenaState.BLIND) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "デスマッチ提案はベット受付中（BETTING/BLIND）のみ可能です。（現在: "
                    + state.getDisplayName() + "）");
            return;
        }

        // 闘技者チェック
        if (!session.isFighter(player.getUniqueId())) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "闘技者のみデスマッチを提案できます。");
            return;
        }

        // 既にデスマッチ成立済み
        if (session.getMatchMode() == MatchMode.DEATHMATCH) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "既にデスマッチが成立しています。");
            return;
        }

        // 投票中チェック
        if (manager.getDeathmatchChallenge() != null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "現在投票中のデスマッチ提案があります。終了をお待ちください。");
            return;
        }

        // 提案回数チェック
        int maxProposals = plugin.getConfig().getInt("deathmatch.max-proposals", 2);
        if (manager.getProposalCount() >= maxProposals) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "デスマッチ提案の上限回数（" + maxProposals + "回）に達しました。");
            return;
        }

        // 金額パース
        if (amountStr.equalsIgnoreCase("all")) {
            // ALL-INデスマッチ
            String error = manager.proposeDeathmatchAllIn(player.getUniqueId());
            if (error != null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + error);
            }
            return;
        }

        long totalAmount;
        try {
            totalAmount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "金額は数値または 'all' で指定してください。");
            return;
        }
        if (totalAmount <= 0) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "金額は1以上を指定してください。");
            return;
        }

        // 提案実行（バリデーションはArenaManager内部で実施）
        String error = manager.proposeDeathmatch(player.getUniqueId(), totalAmount);
        if (error != null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + error);
        }
        // 成功時のブロードキャストはArenaManager側で行う
    }

    /**
     * 投票を処理する（yes/no）。
     */
    private void handleVote(CommandSender sender, ArenaManager manager, boolean accept) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        ArenaSession session = manager.getActiveSession();
        if (session == null) return;

        // 闘技者チェック
        if (!session.isFighter(player.getUniqueId())) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "闘技者のみ投票できます。");
            return;
        }

        // 投票中チェック
        DeathmatchChallenge challenge = manager.getDeathmatchChallenge();
        if (challenge == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "現在投票中のデスマッチ提案はありません。");
            return;
        }

        // 二重投票チェック
        if (challenge.hasVoted(player.getUniqueId())) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "既に投票済みです。投票は変更できません。");
            return;
        }

        // 投票実行
        String error = manager.castDeathmatchVote(player.getUniqueId(), accept);
        if (error != null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + error);
        }
    }

    /**
     * 提案キャンセルを処理する。
     */
    private void handleCancel(CommandSender sender, ArenaManager manager, ArenaSession session) {
        DeathmatchChallenge challenge = manager.getDeathmatchChallenge();

        // 成立済みDMのキャンセル
        if (challenge == null && session.getMatchMode() == MatchMode.DEATHMATCH) {
            // CLOSED以降は管理者のみ
            if (session.getState() == ArenaState.CLOSED) {
                if (!sender.hasPermission("arenacore.admin")) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "ベット締切後のデスマッチキャンセルは管理者のみ可能です。");
                    return;
                }
            }
            // DMモード解除
            session.setMatchMode(MatchMode.NORMAL);
            session.setDeathmatchEntryFee(0);
            session.setDeathmatchPool(0);
            session.setDeathmatchAllIn(false);
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "デスマッチがキャンセルされました。ベットはそのまま有効です。");
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            return;
        }

        // 投票中の提案キャンセル
        if (challenge == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "キャンセルするデスマッチ提案がありません。");
            return;
        }

        // 権限チェック: 提案者 or 管理者
        boolean isProposer = (sender instanceof Player p)
                && p.getUniqueId().equals(challenge.getProposer());
        boolean isAdmin = sender.hasPermission("arenacore.admin");

        // CLOSED以降は管理者のみ
        if (session.getState() == ArenaState.CLOSED && !isAdmin) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "ベット締切後のキャンセルは管理者のみ可能です。");
            return;
        }

        if (!isProposer && !isAdmin) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "提案のキャンセルは提案者または管理者のみ可能です。");
            return;
        }

        manager.cancelDeathmatch();
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                + "✗ デスマッチ提案がキャンセルされました。");
    }

    /**
     * 投票状況を表示する。
     */
    private void handleInfo(CommandSender sender, ArenaManager manager, ArenaSession session) {
        DeathmatchChallenge challenge = manager.getDeathmatchChallenge();

        if (challenge == null) {
            if (session.getMatchMode() == MatchMode.DEATHMATCH) {
                if (session.isDeathmatchAllIn()) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                            + "ALL-IN デスマッチ成立済み");
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                            + "全財産を賭け合い、勝者チームが総取り！");
                } else {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                            + "デスマッチ成立済み");
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                            + "参加費: " + ChatColor.YELLOW
                            + ChipManager.formatAmount(session.getDeathmatchEntryFee()) + " E / 人");
                }
            } else {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                        + "現在デスマッチ提案はありません。");
                int remaining = plugin.getConfig().getInt("deathmatch.max-proposals", 2)
                        - manager.getProposalCount();
                if (remaining > 0) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                            + "残り提案可能回数: " + ChatColor.WHITE + remaining + "回");
                }
            }
            return;
        }

        // 投票中の情報表示
        sender.sendMessage(ArenaMessages.SEPARATOR);
        if (challenge.isAllIn()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                    + "ALL-IN デスマッチ投票中");
        } else {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                    + "デスマッチ投票中");
        }

        Player proposer = Bukkit.getPlayer(challenge.getProposer());
        String proposerName = proposer != null ? proposer.getName() : "???";
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "提案者: " + ChatColor.WHITE + proposerName);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "総額: " + ChatColor.YELLOW
                + ChipManager.formatAmount(challenge.getTotalPool()) + " E"
                + ChatColor.GRAY + " (1人あたり: "
                + ChatColor.YELLOW + ChipManager.formatAmount(challenge.getPerPersonFee()) + " E"
                + ChatColor.GRAY + ")");

        // 各チームの投票状況
        StringBuilder sb = new StringBuilder();
        sb.append(ArenaMessages.PREFIX).append(ChatColor.GRAY);
        boolean first = true;
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) continue;
            if (!first) sb.append(ChatColor.GRAY).append(" | ");
            ChatColor teamColor = session.getTeamColor(team);
            sb.append(teamColor).append(team).append(": ")
                    .append(ChatColor.WHITE).append(challenge.getVoteStatus(team));
            first = false;
        }
        sender.sendMessage(sb.toString());
        sender.sendMessage(ArenaMessages.SEPARATOR);
    }

    /**
     * デスマッチ提案時のクリッカブル投票UIを全闘技者にブロードキャストする。
     *
     * @param session   セッション
     * @param challenge 提案
     * @param entryFee  通常参加費（config）
     */
    public static void broadcastVoteUI(ArenaSession session, DeathmatchChallenge challenge, long entryFee) {
        Player proposer = Bukkit.getPlayer(challenge.getProposer());
        String proposerName = proposer != null ? proposer.getName() : "???";

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        if (challenge.isAllIn()) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                    + "ALL-IN デスマッチ提案");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.WHITE + proposerName
                    + ChatColor.GRAY + " が " + ChatColor.RED + ChatColor.BOLD + "ALL-IN"
                    + ChatColor.RESET + ChatColor.GRAY + " デスマッチを提案しました！");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "全財産を賭け合い、勝者チームが総取り！");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "総額: " + ChatColor.YELLOW
                    + ChipManager.formatAmount(challenge.getTotalPool()) + " E"
                    + ChatColor.GRAY + "（平均: "
                    + ChipManager.formatAmount(challenge.getPerPersonFee()) + " E / 人）");
        } else {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                    + "デスマッチ提案");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.WHITE + proposerName
                    + ChatColor.GRAY + " が " + ChatColor.YELLOW
                    + ChipManager.formatAmount(challenge.getTotalPool()) + " E"
                    + ChatColor.GRAY + " のデスマッチを提案しました！");
        }

        if (!challenge.isAllIn()) {
            String feeInfo = "(1人あたり: " + ChipManager.formatAmount(challenge.getPerPersonFee()) + " E";
            if (entryFee > 0) {
                feeInfo += " + 参加費" + ChipManager.formatAmount(entryFee) + " E"
                        + " = " + ChipManager.formatAmount(challenge.getPerPersonFee() + entryFee) + " E";
            }
            feeInfo += ")";
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY + feeInfo);
        }
        Bukkit.broadcastMessage("");

        // クリッカブルボタンを闘技者に送信
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "↓ クリックして投票 ↓");

        // TextComponent ベースのボタンを生成
        TextComponent prefix = new TextComponent(ArenaMessages.PREFIX);

        TextComponent yesBtn = new TextComponent("  ▶ 賛成する ");
        yesBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        yesBtn.setBold(true);
        yesBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arena deathmatch yes"));
        yesBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("クリックで賛成票を投じます")));

        TextComponent spacer = new TextComponent("    ");

        TextComponent noBtn = new TextComponent("  ▶ 反対する ");
        noBtn.setColor(net.md_5.bungee.api.ChatColor.RED);
        noBtn.setBold(true);
        noBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arena deathmatch no"));
        noBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("クリックで反対票を投じます")));

        // 全プレイヤーにクリッカブルメッセージを送信
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(prefix, yesBtn, spacer, noBtn);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "(コマンド入力: /arena deathmatch yes または no)");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                + "⏱ 20秒以内に投票してください");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    /**
     * 投票進捗をブロードキャストする。
     *
     * @param session   セッション
     * @param challenge 提案
     * @param remaining 残り秒数
     */
    public static void broadcastVoteProgress(ArenaSession session, DeathmatchChallenge challenge, int remaining) {
        StringBuilder sb = new StringBuilder();
        sb.append(ArenaMessages.PREFIX).append(ChatColor.GRAY).append("📊 ");
        boolean first = true;
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) continue;
            if (!first) sb.append(ChatColor.GRAY).append(" | ");
            ChatColor teamColor = session.getTeamColor(team);
            sb.append(teamColor).append(team).append(": ")
                    .append(ChatColor.WHITE).append(challenge.getVoteStatus(team));
            first = false;
        }
        sb.append(ChatColor.GRAY).append("  [残り").append(remaining).append("秒]");
        Bukkit.broadcastMessage(sb.toString());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(
                    List.of("all", "yes", "no", "cancel", "info"), args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena deathmatch <金額|all|yes|no|cancel|info>";
    }
}
