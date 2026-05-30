package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * {@code /arena} コマンドハンドラ（管理者用）。
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "create", "team", "region", "open", "start", "win", "cancel", "status");
    private static final List<String> TEAM_SUBS = Arrays.asList("add", "list");
    private static final List<String> REGION_SUBS = Arrays.asList("bet", "team");

    private final ArenaCore plugin;

    public ArenaCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "create":  handleCreate(sender, args); break;
            case "team":    handleTeam(sender, args); break;
            case "region":  handleRegion(sender, args); break;
            case "open":    handleOpen(sender); break;
            case "start":   handleStart(sender); break;
            case "win":     handleWin(sender, args); break;
            case "cancel":  handleCancel(sender); break;
            case "status":  handleStatus(sender); break;
            default:        sendUsage(sender); break;
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "使い方: /arena create <名前> <チーム1> <チーム2> [チーム3...]");
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        if (manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "既にセッションが稼働中です。先に /arena cancel で中止してください。");
            return;
        }

        String name = args[1];
        List<String> teamNames = new ArrayList<>();
        for (int i = 2; i < args.length; i++) teamNames.add(args[i]);

        ArenaSession session = manager.createArena(name, teamNames);
        if (session == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セッション作成に失敗しました。");
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "⚔ 闘技場「" + name + "」が開設されました！ ⚔");
        Bukkit.broadcastMessage("");
        for (int i = 0; i < teamNames.size(); i++) {
            ChatColor color = ArenaMessages.getTeamColor(i);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + teamNames.get(i));
        }

        long entryFee = plugin.getConfig().getLong("entry-fee", 0);
        if (entryFee > 0) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "参加費: " + ChatColor.YELLOW + ChipManager.formatAmount(entryFee) + " E");
        }
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: /arena team <add|list>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add":  handleTeamAdd(sender, args); break;
            case "list": handleTeamList(sender); break;
            default:
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: /arena team <add|list>");
                break;
        }
    }

    private void handleTeamAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "使い方: /arena team add <チーム名> <プレイヤー>");
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession() || manager.getActiveSession().getState() != ArenaState.SETUP) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セットアップ中のみチーム編集可能です。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        String teamName = args[2];
        if (!session.hasTeam(teamName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "チーム「" + teamName + "」は存在しません。");
            return;
        }

        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "プレイヤー「" + args[3] + "」が見つかりません。");
            return;
        }

        if (session.isFighter(target.getUniqueId())) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + target.getName() + " は既にチーム「" + session.getPlayerTeam(target.getUniqueId()) + "」に所属しています。");
            return;
        }

        if (!manager.addTeamMember(teamName, target)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "追加に失敗しました。");
            return;
        }

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + target.getName() + " が " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に参加しました！");
    }

    private void handleTeamList(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        sender.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD
                + "═══ 闘技場「" + session.getName() + "」チーム一覧 ═══");

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = ArenaMessages.getTeamColor(i);
            List<UUID> members = session.getTeamMembers(team);
            sender.sendMessage("  " + color + ChatColor.BOLD + "■ " + team
                    + ChatColor.RESET + ChatColor.GRAY + " (" + members.size() + "人)");
            if (members.isEmpty()) {
                sender.sendMessage("    " + ChatColor.GRAY + "(メンバーなし)");
            } else {
                for (UUID memberId : members) {
                    Player member = Bukkit.getPlayer(memberId);
                    String memberName = member != null ? member.getName() : "???";
                    boolean eliminated = manager.getEliminatedPlayers().contains(memberId);
                    sender.sendMessage("    " + (eliminated
                            ? ChatColor.STRIKETHROUGH + memberName + ChatColor.RESET + ChatColor.RED + " (死亡)"
                            : ChatColor.WHITE + memberName));
                }
            }
        }
    }

    private void handleRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "プレイヤーのみ使用できます。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "使い方: /arena region <bet|team> <チーム名>");
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }
        ArenaSession session = manager.getActiveSession();

        RegionManager regionManager = plugin.getRegionManager();
        if (!regionManager.isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "WorldEdit が必要です。");
            return;
        }

        String teamName = args[2];
        if (!session.hasTeam(teamName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "チーム「" + teamName + "」は存在しません。");
            return;
        }

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

        switch (args[1].toLowerCase()) {
            case "bet":
                if (session.getState() != ArenaState.SETUP && session.getState() != ArenaState.BETTING) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セットアップ中 or 賭け受付中のみ設定できます。");
                    return;
                }
                if (regionManager.setBettingRegion(player, teamName)) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                            + teamColor + ChatColor.BOLD + teamName
                            + ChatColor.RESET + ChatColor.GREEN + " の賭けエリアを設定しました。");
                } else {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "WorldEdit で範囲を選択してからコマンドを実行してください。");
                }
                break;

            case "team":
                if (session.getState() != ArenaState.SETUP) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セットアップ中のみ使用できます。");
                    return;
                }
                List<UUID> playersInArea = regionManager.getPlayersInSelection(player);
                if (playersInArea.isEmpty()) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "選択範囲内にプレイヤーがいません。");
                    return;
                }
                int added = 0;
                for (UUID playerId : playersInArea) {
                    Player target = Bukkit.getPlayer(playerId);
                    if (target != null && !session.isFighter(playerId)) {
                        if (manager.addTeamMember(teamName, target)) added++;
                    }
                }
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                        + added + " 人を " + teamColor + ChatColor.BOLD + teamName
                        + ChatColor.RESET + ChatColor.GREEN + " に配属しました。");
                break;

            default:
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "使い方: /arena region <bet|team> <チーム名>");
                break;
        }
    }

    private void handleOpen(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }

        if (!manager.openBetting()) {
            ArenaSession session = manager.getActiveSession();
            if (session.getState() != ArenaState.SETUP)
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セットアップ中のみ賭け受付を開始できます。");
            else
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "最低2チームにメンバーが必要です。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "💰 賭け受付開始！ 💰");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "各チームの賭けエリアにカーペット（チップ）を置いて賭けよう！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "手数料率: " + ChatColor.YELLOW + String.format("%.0f%%", houseEdge * 100));

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = ArenaMessages.getTeamColor(i);
            boolean hasRegion = plugin.getRegionManager().hasBettingRegion(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + session.getTeamSize(team) + "人)"
                    + (hasRegion ? ChatColor.GREEN + " ✔エリア設定済" : ChatColor.RED + " ✘エリア未設定"));
        }
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    private void handleStart(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }

        if (!manager.startMatch()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "賭け受付中のみ試合を開始できます。");
            return;
        }

        ArenaSession session = manager.getActiveSession();

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD + "⚔ 試合開始！ ⚔");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + "賭けは締め切りました！");
        Bukkit.broadcastMessage("");
        plugin.getBettingManager().broadcastOdds(session);
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    private void handleWin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: /arena win <チーム名>");
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }

        if (!manager.declareWinner(args[1])) {
            ArenaSession session = manager.getActiveSession();
            if (session == null)
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セッションがありません。");
            else if (session.getState() != ArenaState.ACTIVE)
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "試合中のみ勝者を宣言できます。");
            else
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "チーム「" + args[1] + "」が見つかりません。");
        }
    }

    private void handleCancel(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }
        if (manager.cancelArena()) {
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "闘技場がキャンセルされました。賭け金・参加費は返金されます。");
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        }
    }

    private void handleStatus(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "現在、闘技場セッションはありません。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        sender.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD
                + "═══ 闘技場「" + session.getName() + "」═══");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "状態: "
                + ChatColor.YELLOW + session.getState().getDisplayName());
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "配当方式: "
                + ChatColor.YELLOW + plugin.getConfig().getString("payout-method", "pari-mutuel"));
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "勝利条件: "
                + ChatColor.YELLOW + plugin.getConfig().getString("win-condition", "last-team-standing"));
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "総プール: "
                + ChatColor.YELLOW + ChipManager.formatAmount(session.getTotalPool()) + " E");

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = ArenaMessages.getTeamColor(i);
            sender.sendMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + session.getTeamSize(team) + "人)"
                    + " | 賭け: " + ChatColor.WHITE + ChipManager.formatAmount(session.getTeamPool(team)) + " E"
                    + " | スコア: " + ChatColor.WHITE + session.getScore(team));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /arena create <名前> <チーム1> <チーム2> [...]");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team add <チーム名> <プレイヤー>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team list");
        sender.sendMessage(ChatColor.YELLOW + "  /arena region bet <チーム名>" + ChatColor.GRAY + " ← WE選択範囲を賭けエリアに");
        sender.sendMessage(ChatColor.YELLOW + "  /arena region team <チーム名>" + ChatColor.GRAY + " ← WE選択範囲内のプレイヤーをチームに");
        sender.sendMessage(ChatColor.YELLOW + "  /arena open" + ChatColor.GRAY + " ← 賭け受付開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena start" + ChatColor.GRAY + " ← 試合開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena win <チーム名>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena cancel");
        sender.sendMessage(ChatColor.YELLOW + "  /arena status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(input)) result.add(sub);
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            switch (args[0].toLowerCase()) {
                case "team":
                    for (String sub : TEAM_SUBS) { if (sub.startsWith(input)) result.add(sub); }
                    break;
                case "region":
                    for (String sub : REGION_SUBS) { if (sub.startsWith(input)) result.add(sub); }
                    break;
                case "win":
                    ArenaManager m = plugin.getArenaManager();
                    if (m.hasActiveSession()) {
                        for (String team : m.getActiveSession().getTeamNames()) {
                            if (team.toLowerCase().startsWith(input)) result.add(team);
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            String input = args[2].toLowerCase();
            ArenaManager m = plugin.getArenaManager();
            if (("team".equalsIgnoreCase(args[0]) && "add".equalsIgnoreCase(args[1]))
                    || "region".equalsIgnoreCase(args[0])) {
                if (m.hasActiveSession()) {
                    for (String team : m.getActiveSession().getTeamNames()) {
                        if (team.toLowerCase().startsWith(input)) result.add(team);
                    }
                }
            }
        } else if (args.length == 4 && "team".equalsIgnoreCase(args[0]) && "add".equalsIgnoreCase(args[1])) {
            String input = args[3].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) result.add(p.getName());
            }
        }
        return result;
    }
}
