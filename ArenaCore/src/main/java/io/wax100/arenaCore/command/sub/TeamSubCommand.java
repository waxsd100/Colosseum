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
 * {@code /arena team <list|area|dest>} を処理する。
 *
 * <p>第2階層のサブコマンドとして list/area/dest を持つ。
 * チームメンバーの登録は待機場による自動登録（{@code /arena start} 時）に一本化されている。
 */
public class TeamSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("list", "area", "dest");

    private final ArenaCore plugin;

    public TeamSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // args: [sub, ...]  例: ["add", "チーム名", "プレイヤー"]
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "area" -> handleArea(sender, args);
            case "dest" -> handleDest(sender, args);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
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
            ChatColor color = ArenaMessages.getTeamColor(i);
            List<UUID> members = session.getTeamMembers(team);

            String label = session.isMobTeam(team)
                    ? "[MOB] " + session.getEffectiveTeamSize(team) + "体"
                    : members.size() + "人";
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
                "/arena team area <チーム名>")) return;

        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        String teamName = args[1];
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        if (!plugin.getRegionManager().isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        TeamAreaConfig newConfig = CommandHelper.createAreaConfigFromSelection(player);
        if (newConfig == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
            return;
        }

        // 既存の destination を引き継ぎ
        TeamAreaConfig existing = session.getTeamAreaConfig(teamName);
        if (existing != null) {
            newConfig.setDestination(existing.getDestination());
        }
        session.setTeamAreaConfig(teamName, newConfig);

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

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
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        TeamAreaConfig config = session.getTeamAreaConfig(teamName);
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + String.format(ArenaMessages.MSG_AREA_NOT_SET_FMT, "/arena team area " + teamName));
            return;
        }

        config.setDestination(player.getLocation());

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " のTP先を現在地に設定しました。");
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // args[0]=sub args[1]=teamName args[2]=playerName
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("area".equals(sub) || "dest".equals(sub)) {
                return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[1]);
            }
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena team <list|area|dest>";
    }


}
