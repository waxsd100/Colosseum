package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.AreaStore;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena area <save|list|delete|info>} を処理する。
 *
 * <p>待機場の保存・一覧・削除・情報表示を管理する。
 */
public class AreaSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = List.of("save", "list", "delete", "info");

    private final ArenaCore plugin;

    public AreaSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "save" -> handleSave(sender, args);
            case "list" -> handleList(sender);
            case "delete" -> handleDelete(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── save ──

    /**
     * {@code /arena area save <名前>} — WE選択範囲を待機場として保存。
     * {@code /arena area save <名前> dest} — 保存済み待機場のTP先を現在地に設定。
     */
    private void handleSave(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2, "/arena area save <名前> [dest]")) return;

        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        String name = args[1];
        AreaStore areaStore = plugin.getAreaStore();

        // dest サブアクション: 既存待機場のTP先を現在地に設定
        if (args.length >= 3 && "dest".equalsIgnoreCase(args[2])) {
            TeamAreaConfig config = areaStore.load(name);
            if (config == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "待機場「" + name + "」が見つかりません。先に保存してください。");
                return;
            }
            config.setDestination(player.getLocation());
            areaStore.save(name, config);
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                    + "待機場「" + ChatColor.WHITE + name + ChatColor.GREEN + "」のTP先を設定しました。");
            return;
        }

        // 通常保存: WE選択範囲から待機場を作成
        TeamAreaConfig config = CommandHelper.createAreaConfigFromSelection(player);
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
            return;
        }

        // 既存のTP先を引き継ぐ
        if (areaStore.exists(name)) {
            TeamAreaConfig existing = areaStore.load(name);
            if (existing != null && existing.getDestination() != null) {
                config.setDestination(existing.getDestination());
            }
        }

        areaStore.save(name, config);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "待機場を保存しました: " + ChatColor.WHITE + name);
    }

    // ── list ──

    private void handleList(CommandSender sender) {
        AreaStore areaStore = plugin.getAreaStore();
        List<String> areas = areaStore.list();

        if (areas.isEmpty()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "保存済み待機場はありません。");
            return;
        }

        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "保存済み待機場一覧:");
        for (String area : areas) {
            sender.sendMessage("  " + ChatColor.YELLOW + area);
        }
    }

    // ── delete ──

    private void handleDelete(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2, "/arena area delete <名前>")) return;

        String name = args[1];
        AreaStore areaStore = plugin.getAreaStore();

        if (areaStore.delete(name)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                    + "待機場を削除しました: " + ChatColor.WHITE + name);
        } else {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "待機場「" + name + "」が見つかりません。");
        }
    }

    // ── info ──

    private void handleInfo(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2, "/arena area info <名前>")) return;

        String name = args[1];
        AreaStore areaStore = plugin.getAreaStore();
        TeamAreaConfig config = areaStore.load(name);

        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "待機場「" + name + "」が見つかりません。");
            return;
        }

        sender.sendMessage(ArenaMessages.SEPARATOR);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "待機場情報: " + ChatColor.WHITE + name);
        sender.sendMessage(ChatColor.GRAY + "  ワールド: " + ChatColor.WHITE + config.getWorldName());
        sender.sendMessage(ChatColor.GRAY + "  範囲: " + ChatColor.WHITE
                + "(" + config.getMinX() + ", " + config.getMinY() + ", " + config.getMinZ() + ")"
                + ChatColor.GRAY + " → " + ChatColor.WHITE
                + "(" + config.getMaxX() + ", " + config.getMaxY() + ", " + config.getMaxZ() + ")");

        Location dest = config.getDestination();
        if (dest != null && dest.getWorld() != null) {
            sender.sendMessage(ChatColor.GRAY + "  TP先: " + ChatColor.WHITE
                    + dest.getWorld().getName()
                    + " (" + String.format("%.1f", dest.getX())
                    + ", " + String.format("%.1f", dest.getY())
                    + ", " + String.format("%.1f", dest.getZ()) + ")");
        } else {
            sender.sendMessage(ChatColor.GRAY + "  TP先: " + ChatColor.RED + "未設定");
        }
        sender.sendMessage(ArenaMessages.SEPARATOR);
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("delete".equals(sub) || "info".equals(sub) || "save".equals(sub)) {
                return CommandHelper.filterStartsWith(plugin.getAreaStore().list(), args[1]);
            }
        }
        if (args.length == 3 && "save".equalsIgnoreCase(args[0])) {
            return CommandHelper.filterStartsWith(List.of("dest"), args[2]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena area <save|list|delete|info>";
    }
}
