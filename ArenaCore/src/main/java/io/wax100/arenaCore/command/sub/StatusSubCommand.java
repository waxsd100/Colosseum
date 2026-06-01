package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /arena status} — セッション状態表示を処理する。
 */
public class StatusSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public StatusSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
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
                + ChatColor.YELLOW + "パリミュチュエル（天引き分配）");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "勝利条件: "
                + ChatColor.YELLOW + plugin.getConfig().getString("win-condition", "last-team-standing"));
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "総プール: "
                + ChatColor.YELLOW + ChipManager.formatAmount(session.getTotalPool()) + " E");

        List<String> teamNames = session.getTeamNames();
        for (String team : teamNames) {
            ChatColor color = session.getTeamColor(team);

            String label = ArenaMessages.formatTeamLabel(session, team);

            sender.sendMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + label + ")"
                    + " | ベット: " + ChatColor.WHITE + ChipManager.formatAmount(session.getTeamPool(team)) + " E"
                    + " | スコア: " + ChatColor.WHITE + session.getScore(team));

            // 待機場情報
            TeamAreaConfig areaConfig = session.getTeamAreaConfig(team);
            if (areaConfig != null) {
                StringBuilder areaInfo = new StringBuilder();
                areaInfo.append(ChatColor.GRAY).append("    待機場: ");

                if (session.isMobTeam(team)) {
                    List<LivingEntity> mobs = areaConfig.scanEntities();
                    areaInfo.append(ChatColor.WHITE).append(mobs.size()).append("体");
                    if (!mobs.isEmpty()) {
                        areaInfo.append(ChatColor.GRAY).append(" (");
                        Map<String, Integer> typeCounts = new LinkedHashMap<>();
                        for (LivingEntity mob : mobs) {
                            typeCounts.merge(mob.getName(), 1, Integer::sum);
                        }
                        boolean first = true;
                        for (var entry : typeCounts.entrySet()) {
                            if (!first) areaInfo.append(", ");
                            areaInfo.append(entry.getKey());
                            if (entry.getValue() > 1) areaInfo.append("x").append(entry.getValue());
                            first = false;
                        }
                        areaInfo.append(")");
                    }
                }

                // プレイヤー情報（全チーム共通）
                List<Player> players = areaConfig.scanPlayers();
                if (!players.isEmpty()) {
                    if (session.isMobTeam(team)) {
                        areaInfo.append(ChatColor.GRAY).append(" + ");
                    }
                    areaInfo.append(ChatColor.WHITE).append(players.size()).append("人");
                    areaInfo.append(ChatColor.GRAY).append(" (");
                    for (int j = 0; j < players.size(); j++) {
                        if (j > 0) areaInfo.append(", ");
                        areaInfo.append(players.get(j).getName());
                    }
                    areaInfo.append(")");
                } else if (!session.isMobTeam(team)) {
                    areaInfo.append(ChatColor.WHITE).append("0人");
                }

                // TP先
                Location dest = areaConfig.getDestination();
                if (dest != null) {
                    areaInfo.append(ChatColor.GREEN).append(" TP✔");
                } else {
                    areaInfo.append(ChatColor.RED).append(" TP✘");
                }

                sender.sendMessage(areaInfo.toString());
            } else {
                sender.sendMessage(ChatColor.GRAY + "    待機場: " + ChatColor.RED + "未設定");
            }
        }
    }

    @Override
    public String getUsage() {
        return "/arena status";
    }
}
