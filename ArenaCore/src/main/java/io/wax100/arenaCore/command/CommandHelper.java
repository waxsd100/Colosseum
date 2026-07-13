package io.wax100.arenaCore.command;

import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.CylinderFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * コマンドハンドラ共通ユーティリティ。
 *
 * <p>バリデーション・タブ補完フィルタ・WorldEdit 連携など、
 * 各サブコマンドで繰り返し使用するロジックを集約する。
 */
public final class CommandHelper {

    private CommandHelper() {}

    // ── バリデーション ──

    /**
     * 送信者がプレイヤーであることを検証する。
     *
     * @return プレイヤーの場合その Player、そうでなければ null（エラーメッセージ送信済み）
     */
    public static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_PLAYER_ONLY);
        return null;
    }

    /**
     * アクティブセッションの存在を検証する。
     *
     * @return セッションが存在すれば ArenaSession、なければ null（エラーメッセージ送信済み）
     */
    public static ArenaSession requireActiveSession(CommandSender sender, ArenaManager manager) {
        if (manager.hasActiveSession()) {
            return manager.getActiveSession();
        }
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_NO_SESSION);
        return null;
    }

    /**
     * セッションが指定状態であることを検証する。
     *
     * @return 条件を満たせば ArenaSession、そうでなければ null（エラーメッセージ送信済み）
     */
    public static ArenaSession requireSessionInState(CommandSender sender, ArenaManager manager,
                                                               ArenaState requiredState, String errorMsg) {
        ArenaSession session = requireActiveSession(sender, manager);
        if (session == null) return null;
        if (session.getState() != requiredState) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + errorMsg);
            return null;
        }
        return session;
    }

    /**
     * チーム名がセッションに存在しない場合にエラーメッセージを送信し、中断を指示する。
     *
     * <p>呼び出し側では {@code if (abortIfTeamNotFound(...)) return;} のパターンで使用する。
     *
     * @return チームが存在しなければ true（中断すべき）、存在すれば false
     */
    public static boolean abortIfTeamNotFound(CommandSender sender, ArenaSession session, String teamName) {
        if (session.hasTeam(teamName)) return false;
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                + "チーム「" + teamName + "」は存在しません。");
        return true;
    }

    /**
     * 引数の数が足りているか検証する。
     *
     * @return 足りていれば true、そうでなければ false（使い方メッセージ送信済み）
     */
    public static boolean requireArgs(CommandSender sender, String [] args, int minLength, String usage) {
        if (args.length >= minLength) return true;
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + usage);
        return false;
    }

    // ── WorldEdit 連携 ──

    /**
     * WorldEdit の選択範囲から TeamAreaConfig を生成する。
     *
     * @return 生成した TeamAreaConfig。選択範囲がない場合 null
     */
    public static TeamAreaConfig createAreaConfigFromSelection(Player player) {
        try {
            com.sk89q.worldedit.entity.Player wePlayer =
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player);
            com.sk89q.worldedit.regions.Region region =
                    com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager()
                            .get(wePlayer).getSelection(wePlayer.getWorld());
            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
            String worldName = player.getWorld().getName();
            @SuppressWarnings("deprecation")
            TeamAreaConfig result = new TeamAreaConfig(worldName,
                    min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            return result;
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            return null;
        } catch (NoClassDefFoundError | Exception e) {
            // WorldEdit が未インストールまたは互換性のないバージョンの場合
            return null;
        }
    }

    /**
     * WorldEdit の選択範囲から ArenaFieldConfig を生成する。
     *
     * @return 生成した ArenaFieldConfig。選択範囲がない場合 null
     */
    public static ArenaFieldConfig createFieldConfigFromSelection(Player player) {
        try {
            com.sk89q.worldedit.entity.Player wePlayer =
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player);
            com.sk89q.worldedit.regions.Region region =
                    com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager()
                            .get(wePlayer).getSelection(wePlayer.getWorld());
            String worldName = player.getWorld().getName();

            // 円柱選択の場合
            if (region instanceof com.sk89q.worldedit.regions.CylinderRegion cyl) {
                com.sk89q.worldedit.math.Vector3 exactCenter = cyl.getCenter();
                double radius = cyl.getRadius().getX();
                int minY = cyl.getMinimumY();
                int maxY = cyl.getMaximumY();
                return CylinderFieldConfig.of(worldName,
                        exactCenter.getX(), exactCenter.getZ(), radius, minY, maxY);
            }

            // デフォルト: 直方体選択
            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
            return ArenaFieldConfig.of(worldName,
                    min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    // ── タブ補完ユーティリティ ──

    /**
     * 入力プレフィックスに一致する候補をフィルタリングする。
     *
     * @param candidates 候補リスト
     * @param input      ユーザ入力（小文字化前提）
     * @return フィルタ済み候補
     */
    public static List<String> filterStartsWith(Collection<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * アクティブセッションのチーム名候補を取得する。
     *
     * @param manager ArenaManager
     * @param input   ユーザ入力
     * @return フィルタ済みチーム名リスト
     */
    public static List<String> getTeamNameCandidates(ArenaManager manager, String input) {
        if (!manager.hasActiveSession()) return List.of();
        return filterStartsWith(manager.getActiveSession().getTeamNames(), input);
    }
}
