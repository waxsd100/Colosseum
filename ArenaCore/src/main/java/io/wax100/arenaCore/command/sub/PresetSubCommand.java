package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.ArenaPresetStore;
import io.wax100.arenaCore.manager.ArenaPresetStore.PresetData;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena preset <save|load|list|delete>} を処理する。
 *
 * <p>アリーナプリセットの保存・ロード・一覧・削除を管理する。
 */
public class PresetSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = List.of("save", "load", "list", "delete");

    private final ArenaCore plugin;

    public PresetSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "save" -> handleSave(sender, args);
            case "load" -> handleLoad(sender, args);
            case "list" -> handleList(sender);
            case "delete" -> handleDelete(sender, args);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── save ──

    private void handleSave(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        // save [name] — 省略時はセッション名
        String name = args.length >= 2 ? args[1] : session.getName();

        ArenaPresetStore presetStore = plugin.getPresetStore();
        presetStore.save(name, session, plugin.getRegionManager());

        sender.sendMessage(ArenaMessages.MSG_PRESET_SAVED + ChatColor.WHITE + name);
    }

    // ── load ──

    private void handleLoad(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2, "/arena preset load <名前>")) return;

        String name = args[1];
        ArenaManager manager = plugin.getArenaManager();

        if (manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SESSION_ALREADY_ACTIVE);
            return;
        }

        ArenaPresetStore presetStore = plugin.getPresetStore();
        PresetData data = presetStore.load(name);
        if (data == null) {
            sender.sendMessage(ArenaMessages.MSG_PRESET_NOT_FOUND + ChatColor.WHITE + name);
            return;
        }

        ArenaSession session = manager.createFromPreset(data);
        if (session == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SESSION_CREATE_FAILED);
            return;
        }

        sender.sendMessage(ArenaMessages.MSG_PRESET_LOADED + ChatColor.WHITE + name);
    }

    // ── list ──

    private void handleList(CommandSender sender) {
        ArenaPresetStore presetStore = plugin.getPresetStore();
        List<String> presets = presetStore.list();

        if (presets.isEmpty()) {
            sender.sendMessage(ArenaMessages.MSG_PRESET_LIST_EMPTY);
            return;
        }

        sender.sendMessage(ArenaMessages.MSG_PRESET_LIST_HEADER);
        for (String preset : presets) {
            sender.sendMessage("  " + ChatColor.YELLOW + preset);
        }
    }

    // ── delete ──

    private void handleDelete(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2, "/arena preset delete <名前>")) return;

        String name = args[1];
        ArenaPresetStore presetStore = plugin.getPresetStore();

        if (presetStore.delete(name)) {
            sender.sendMessage(ArenaMessages.MSG_PRESET_DELETED + ChatColor.WHITE + name);
        } else {
            sender.sendMessage(ArenaMessages.MSG_PRESET_NOT_FOUND + ChatColor.WHITE + name);
        }
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("load".equals(sub) || "delete".equals(sub)) {
                return CommandHelper.filterStartsWith(plugin.getPresetStore().list(), args[1]);
            }
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena preset <save|load|list|delete>";
    }
}
