package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.FieldStore;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /arena field <set|info|save|load|list|delete>} を処理する。
 *
 * <p>WorldEdit の選択範囲から戦闘エリアを定義し、
 * Schematic の即時保存を行う。
 * save/load/list/delete で戦闘エリア設定を永続化・再利用できる。
 */
public class FieldSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("set", "info", "save", "load", "list", "delete");
    private static final int FIELD_SIZE_WARNING = 500_000;

    private final ArenaCore plugin;

    public FieldSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "set"    -> handleSet(sender);
            case "info"   -> handleInfo(sender);
            case "save"   -> handleSave(sender, args);
            case "load"   -> handleLoad(sender, args);
            case "list"   -> handleFieldList(sender);
            case "delete" -> handleDelete(sender, args);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── set ──

    private void handleSet(CommandSender sender) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;

        if (!plugin.getRegionManager().isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        ArenaFieldConfig fieldConfig = createFieldConfigFromSelection(player);
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
                field.getWorldName(),
                field.getMinX(), field.getMinY(), field.getMinZ(),
                field.getMaxX(), field.getMaxY(), field.getMaxZ(),
                field.getBlockCount()));
    }

    // ── WorldEdit 連携 ──

    /**
     * WorldEdit の選択範囲から ArenaFieldConfig を生成する。
     *
     * @param player 選択範囲を持つプレイヤー
     * @return 生成した ArenaFieldConfig。選択範囲がない場合 {@code null}
     */
    private static ArenaFieldConfig createFieldConfigFromSelection(Player player) {
        return CommandHelper.createFieldConfigFromSelection(player);
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("load".equals(sub) || "delete".equals(sub) || "save".equals(sub)) {
                // 保存済み戦闘エリア名の補完
                return CommandHelper.filterStartsWith(
                        plugin.getFieldStore().list(), args[1]);
            }
        }
        return List.of();
    }

    // ── save (保存) ──

    private void handleSave(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena field save <名前>")) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        ArenaFieldConfig fieldConfig = session.getFieldConfig();
        if (fieldConfig == null) {
            sender.sendMessage(ArenaMessages.MSG_FIELD_NOT_SET);
            return;
        }

        String saveName = args[1];
        FieldStore store = plugin.getFieldStore();
        store.save(saveName, fieldConfig);

        // Schematic ファイルをコピー: arenas/<セッション名>.schem → fields/<名前>.schem
        File srcSchem = new File(plugin.getDataFolder(), "arenas/" + session.getName() + ".schem");
        File dstSchem = store.getFieldFile(saveName);
        if (srcSchem.exists()) {
            try {
                dstSchem.getParentFile().mkdirs();
                Files.copy(srcSchem.toPath(), dstSchem.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "Schematic コピー失敗: " + e.getMessage());
                return;
            }
        } else {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "⚠ Schematic ファイルが見つかりません。地形復元は利用できません。");
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "先に /arena field set で戦闘エリアを設定してください。");
        }

        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "戦闘エリア「" + ChatColor.YELLOW + saveName + ChatColor.GREEN
                + "」を保存しました。");
    }

    // ── load (読込) ──

    private void handleLoad(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena field load <名前>")) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;

        String loadName = args[1];
        FieldStore store = plugin.getFieldStore();
        ArenaFieldConfig fieldConfig = store.load(loadName);
        if (fieldConfig == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "戦闘エリア「" + loadName + "」が見つかりません。");
            return;
        }

        session.setFieldConfig(fieldConfig);

        // Schematic ファイルをコピー: fields/<名前>.schem → arenas/<セッション名>.schem
        File srcSchem = store.getFieldFile(loadName);
        File dstSchem = new File(plugin.getDataFolder(), "arenas/" + session.getName() + ".schem");
        if (srcSchem.exists()) {
            try {
                dstSchem.getParentFile().mkdirs();
                Files.copy(srcSchem.toPath(), dstSchem.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "Schematic コピー失敗: " + e.getMessage());
                return;
            }
        }

        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "戦闘エリア「" + ChatColor.YELLOW + loadName + ChatColor.GREEN
                + "」を読み込みました。");
    }

    // ── list (一覧) ──

    private void handleFieldList(CommandSender sender) {
        FieldStore store = plugin.getFieldStore();
        List<String> names = store.list();

        if (names.isEmpty()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "保存済み戦闘エリアはありません。");
            return;
        }

        sender.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD
                + "═══ 保存済み戦闘エリア一覧 ═══");
        for (String name : names) {
            ArenaFieldConfig config = store.load(name);
            if (config != null) {
                sender.sendMessage("  " + ChatColor.YELLOW + name
                        + ChatColor.GRAY + " (" + config.getWorldName()
                        + ", " + config.getBlockCount() + "ブロック)");
            } else {
                sender.sendMessage("  " + ChatColor.YELLOW + name);
            }
        }
    }

    // ── delete (削除) ──

    private void handleDelete(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 2,
                "/arena field delete <名前>")) return;

        String deleteName = args[1];
        FieldStore store = plugin.getFieldStore();

        if (!store.exists(deleteName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "戦闘エリア「" + deleteName + "」が見つかりません。");
            return;
        }

        store.delete(deleteName);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "戦闘エリア「" + ChatColor.YELLOW + deleteName + ChatColor.GREEN
                + "」を削除しました。");
    }

    @Override
    public String getUsage() {
        return "/arena field <set|info|save|load|list|delete>";
    }
}
