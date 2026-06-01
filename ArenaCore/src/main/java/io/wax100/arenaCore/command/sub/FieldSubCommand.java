package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena field <set|info>} を処理する。
 *
 * <p>WorldEdit の選択範囲から戦闘エリアを定義し、Schematic の即時保存を行う。
 * 永続保存は {@code /arena preset save} で一括して行う。
 */
public class FieldSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = List.of("set", "info");
    private static final int FIELD_SIZE_WARNING = 500_000;

    private final ArenaCore plugin;

    public FieldSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "set"  -> handleSet(sender, args);
            case "info" -> handleInfo(sender);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── set ──

    private void handleSet(CommandSender sender, String [] args) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;

        // WE 選択範囲から設定
        if (!plugin.getRegionManager().isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        ArenaFieldConfig fieldConfig = CommandHelper.createFieldConfigFromSelection(player);
        if (fieldConfig == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
            return;
        }

        session.setFieldConfig(fieldConfig);

        // Schematic 保存
        plugin.getTerrainManager().saveFieldSchematic(fieldConfig, session.getName());

        sender.sendMessage(ArenaMessages.MSG_FIELD_SET);

        // ブロック数警告
        long blockCount = fieldConfig.getBlockCount();
        if (blockCount >= FIELD_SIZE_WARNING) {
            sender.sendMessage(String.format(ArenaMessages.MSG_FIELD_TOO_LARGE_FMT, blockCount));
        }
    }

    // ── info ──

    private void handleInfo(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        ArenaFieldConfig field = session.getFieldConfig();
        if (field == null) {
            sender.sendMessage(ArenaMessages.MSG_FIELD_NOT_SET);
            return;
        }

        sender.sendMessage(String.format(ArenaMessages.MSG_FIELD_INFO_FMT,
                field.worldName(),
                field.minX(), field.minY(), field.minZ(),
                field.maxX(), field.maxY(), field.maxZ(),
                field.getBlockCount()));
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String [] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena field <set|info>";
    }
}
