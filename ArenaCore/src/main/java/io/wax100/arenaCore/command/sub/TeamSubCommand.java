package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /arena team <add|list|area|dest|color>} を処理する。
 *
 * <p>第2階層のサブコマンドとして add/list/area/dest/color を持つ。
 * チームメンバーの登録は待機場による自動登録（{@code /arena start} 時）に一本化されている。
 */
public class TeamSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("add", "list", "area", "dest", "color");

    private final ArenaCore plugin;

    public TeamSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // args: [sub, ...]  例: ["add", "チーム名"]
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "add"  -> handleAdd(sender, args);
            case "list" -> handleList(sender);
            case "area" -> handleArea(sender, args);
            case "dest" -> handleDest(sender, args);
            case "color" -> handleColor(sender, args);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }


    // ── add (チーム追加) ──

    private void handleAdd(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena team add <チーム名>")) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;

        String teamName = args[1];
        if (session.hasTeam(teamName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "チーム「" + teamName + "」は既に存在します。");
            return;
        }

        session.addTeam(teamName);
        ChatColor teamColor = session.getTeamColor(teamName);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "チーム " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " が追加されました！");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "→ " + ChatColor.YELLOW + "/arena team area " + teamName + " [待機場名]"
                + ChatColor.GRAY + " で待機場を設定");
    }

    // ── list ──

    private void handleList(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        sender.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD
                + "═══ 闘技場「" + session.getName() + "」チーム一覧 ═══");

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = session.getTeamColor(team);
            List<UUID> members = session.getTeamMembers(team);

            String label = ArenaMessages.formatTeamLabel(session, team);
            sender.sendMessage("  " + color + ChatColor.BOLD + "■ " + team
                    + ChatColor.RESET + ChatColor.GRAY + " (" + label + ")");

            // 待機場Mob情報
            if (session.isMobTeam(team)) {
                TeamAreaConfig config = session.getTeamAreaConfig(team);
                int mobCount = session.getAliveMobCount(team);
                if (mobCount > 0) {
                    sender.sendMessage("    " + ChatColor.GRAY + "[MOB] 残り"
                            + ChatColor.WHITE + mobCount + "体");
                } else if (config != null) {
                    int waitingCount = config.scanEntities().size();
                    sender.sendMessage("    " + ChatColor.GRAY + "[MOB] 待機場: "
                            + ChatColor.WHITE + waitingCount + "体");
                }
            }

            if (members.isEmpty() && !session.isMobTeam(team)) {
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

    // ── area (待機場設定) ──

    private void handleArea(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena team area <チーム名> [待機場名]")) return;

        String teamName = args[1];
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;
        if (CommandHelper.requireTeamExists(sender, session, teamName)) return;

        TeamAreaConfig newConfig;

        if (args.length >= 3) {
            // 保存済み待機場名を指定
            String areaName = args[2];
            newConfig = plugin.getAreaStore().load(areaName);
            if (newConfig == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "待機場「" + areaName + "」が見つかりません。");
                return;
            }
        } else {
            // WE選択範囲から作成
            Player player = CommandHelper.requirePlayer(sender);
            if (player == null) return;

            if (!plugin.getRegionManager().isWorldEditAvailable()) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
                return;
            }

            newConfig = CommandHelper.createAreaConfigFromSelection(player);
            if (newConfig == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
                return;
            }
        }

        // 既存の destination を引き継ぎ
        TeamAreaConfig existing = session.getTeamAreaConfig(teamName);
        if (existing != null) {
            newConfig.setDestination(existing.getDestination());
        }
        session.setTeamAreaConfig(teamName, newConfig);

        ChatColor teamColor = session.getTeamColor(teamName);

        int count = newConfig.scanPlayers().size();
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " の待機場を設定しました。"
                + ChatColor.GRAY + " (エリア内: " + ChatColor.WHITE + count + "人"
                + ChatColor.GRAY + ")");
    }

    // ── dest (TP先設定) ──

    private void handleDest(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena team dest <チーム名>")) return;

        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        String teamName = args[1];
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;
        if (CommandHelper.requireTeamExists(sender, session, teamName)) return;

        TeamAreaConfig config = session.getTeamAreaConfig(teamName);
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + String.format(ArenaMessages.MSG_AREA_NOT_SET_FMT, "/arena team area " + teamName));
            return;
        }

        config.setDestination(player.getLocation());

        ChatColor teamColor = session.getTeamColor(teamName);
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " のTP先を現在地に設定しました。");
    }

    // ── color (チームカラー設定) ──

    private void handleColor(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 3,
                "/arena team color <チーム名> <色>")) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;

        String teamName = args[1];
        if (CommandHelper.requireTeamExists(sender, session, teamName)) return;

        String colorName = args[2].toUpperCase();
        ChatColor chatColor;
        try {
            chatColor = ChatColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "無効な色です: " + args[2]);
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "使用可能: " + getAvailableColors());
            return;
        }

        // 装飾コード（BOLD, ITALIC等）は弾く
        if (!chatColor.isColor()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + chatColor.name().toLowerCase() + " は装飾コードです。色を指定してください。");
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "使用可能: " + getAvailableColors());
            return;
        }

        session.setTeamColor(teamName, chatColor);
        // Scoreboard Team のカラーも同期
        plugin.getArenaManager().ensureScoreboardTeam(teamName);
        sender.sendMessage(ArenaMessages.PREFIX + chatColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " のチームカラーを "
                + chatColor + chatColor.name().toLowerCase() + ChatColor.GREEN + " に設定しました。");
    }

    private static String getAvailableColors() {
        StringBuilder sb = new StringBuilder();
        for (ChatColor c : ChatColor.values()) {
            if (c.isColor()) {
                if (sb.length() > 0) sb.append(ChatColor.GRAY + ", ");
                sb.append(c).append(c.name().toLowerCase());
            }
        }
        return sb.toString();
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // args[0]=sub args[1]=teamName args[2]=...
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("area".equals(sub) || "dest".equals(sub) || "color".equals(sub)) {
                return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("color".equals(sub)) {
                // 色名の補完
                return CommandHelper.filterStartsWith(
                        Arrays.stream(ChatColor.values())
                                .filter(ChatColor::isColor)
                                .map(c -> c.name().toLowerCase())
                                .toList(),
                        args[2]);
            }
            if ("area".equals(sub)) {
                // 保存済み待機場名の補完
                return CommandHelper.filterStartsWith(
                        plugin.getAreaStore().list(), args[2]);
            }
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena team <add|list|area|dest|color>";
    }


}
