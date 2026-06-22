package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 戦闘エリア外への脱出を監視するリスナー。
 *
 * <p>試合中（ACTIVE 状態）に戦闘員がフィールド範囲外に出ると、
 * 設定秒数のカウントダウン警告を表示し、時間内にエリア内へ
 * 戻らなければ自動的に脱落（死亡扱い）する。
 *
 * <p>カウントダウン中にエリア内に戻った場合は警告が解除される。
 */
public class ArenaOutOfBoundsListener implements Listener {

    /** エリア外プレイヤーの脱出開始時刻（ミリ秒） */
    private final Map<UUID, Long> outOfBoundsStart = new HashMap<>();
    /** エリア外モンスターの脱出開始時刻（ミリ秒） */
    private final Map<UUID, Long> mobOutOfBoundsStart = new HashMap<>();

    private final ArenaCore plugin;
    private BukkitTask countdownTask;

    public ArenaOutOfBoundsListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤー移動時にエリア外判定を行う。
     *
     * <p>ブロック移動がない場合（視点変更のみ）は無視する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // ブロック位置が変わっていなければ無視（軽量化）
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 戦闘員でなければ無視
        if (!session.isFighter(playerId)) return;
        // 既に脱落済みなら無視
        if (manager.getEliminatedPlayers().contains(playerId)) return;

        ArenaFieldConfig field = session.getFieldConfig();
        if (field == null) return;

        if (field.contains(to)) {
            // エリア内に戻った → 警告解除
            if (outOfBoundsStart.remove(playerId) != null) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(
                                ChatColor.GREEN + "✓ 戦闘エリアに戻りました"));
            }
        } else {
            // エリア外 → 初回記録
            outOfBoundsStart.putIfAbsent(playerId, System.currentTimeMillis());
        }
    }

    /**
     * カウントダウンタスクを開始する。
     *
     * <p>試合開始時に {@link ArenaManager} から呼び出される。
     * 1秒ごとにエリア外プレイヤーの残り時間をチェックし、
     * タイムアウトで脱落処理を行う。
     */
    public void startCountdown() {
        stopCountdown();
        outOfBoundsStart.clear();
        mobOutOfBoundsStart.clear();

        int gracePeriod = plugin.getConfig().getInt("out-of-bounds.grace-seconds", 10);
        int mobGracePeriod = plugin.getConfig().getInt("out-of-bounds.mob-grace-seconds", gracePeriod);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ArenaManager manager = plugin.getArenaManager();
            if (!manager.hasActiveSession()
                    || manager.getActiveSession().getState() != ArenaState.ACTIVE) {
                stopCountdown();
                return;
            }

            ArenaSession session = manager.getActiveSession();
            ArenaFieldConfig field = session.getFieldConfig();
            if (field == null) return;

            long now = System.currentTimeMillis();

            // ── プレイヤーのエリア外チェック ──
            checkPlayersOutOfBounds(manager, session, field, now, gracePeriod);

            // ── モンスターのエリア外チェック ──
            checkMobsOutOfBounds(manager, session, field, now, mobGracePeriod);

        }, 0L, 20L); // 1秒ごと
    }

    /**
     * カウントダウンタスクを停止し、追跡データをクリアする。
     *
     * <p>試合終了・キャンセル時に呼び出される。
     */
    public void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        outOfBoundsStart.clear();
        mobOutOfBoundsStart.clear();
    }

    /**
     * プレイヤーのエリア外チェックを行う。
     *
     * @param manager     アリーナマネージャー
     * @param session     アクティブセッション
     * @param field       戦闘フィールド設定
     * @param now         現在時刻（ミリ秒）
     * @param gracePeriod 猶予秒数
     */
    private void checkPlayersOutOfBounds(ArenaManager manager, ArenaSession session,
                                         ArenaFieldConfig field, long now, int gracePeriod) {
        long graceMs = gracePeriod * 1000L;

        Iterator<Map.Entry<UUID, Long>> it = outOfBoundsStart.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long startTime = entry.getValue();

            // 既に脱落済みなら除外
            if (manager.getEliminatedPlayers().contains(playerId)) {
                it.remove();
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }

            // エリア内に戻っているか再チェック（テレポート等で座標が変わった場合）
            if (field.contains(player.getLocation())) {
                it.remove();
                continue;
            }

            long elapsed = now - startTime;
            int remainingSeconds = (int) Math.ceil((graceMs - elapsed) / 1000.0);

            if (elapsed >= graceMs) {
                // タイムアウト → 脱落
                it.remove();

                String team = session.getPlayerTeam(playerId);
                ChatColor teamColor = team != null
                        ? session.getTeamColor(team) : ChatColor.WHITE;

                Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + player.getName()
                        + ChatColor.GRAY + " (" + teamColor + team + ChatColor.GRAY + ")"
                        + ChatColor.RED + " が戦闘エリア外で失格になりました！");

                // Scoreboard Team から削除
                if (team != null) {
                    manager.removeFromScoreboardTeam(team, player);
                }

                manager.onFighterDeath(playerId);
            } else {
                // カウントダウン警告（アクションバー）
                ChatColor urgency = remainingSeconds <= 3 ? ChatColor.RED : ChatColor.YELLOW;
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(
                                urgency + "⚠ 戦闘エリア外！ " + ChatColor.WHITE
                                        + remainingSeconds + "秒" + urgency
                                        + " 以内に戻ってください！"));
            }
        }
    }

    /**
     * トラッキング中モンスターのエリア外チェックを行う。
     *
     * <p>フィールド外にいるMobを検出し、猶予時間後にKillする。
     * エリア内に戻った場合は追跡を解除する。
     *
     * @param manager        アリーナマネージャー
     * @param session        アクティブセッション
     * @param field          戦闘フィールド設定
     * @param now            現在時刻（ミリ秒）
     * @param mobGracePeriod Mob用猶予秒数
     */
    private void checkMobsOutOfBounds(ArenaManager manager, ArenaSession session,
                                      ArenaFieldConfig field, long now, int mobGracePeriod) {
        long graceMs = mobGracePeriod * 1000L;

        // 1. トラッキング中の全Mobを走査し、エリア外のMobを記録
        for (Map.Entry<UUID, String> mobEntry : session.getTrackedMobs().entrySet()) {
            UUID mobId = mobEntry.getKey();
            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null || entity.isDead() || !(entity instanceof LivingEntity)) {
                continue;
            }

            if (field.contains(entity.getLocation())) {
                // エリア内に戻った → 追跡解除
                mobOutOfBoundsStart.remove(mobId);
            } else {
                // エリア外 → 初回記録
                mobOutOfBoundsStart.putIfAbsent(mobId, now);
            }
        }

        // 2. エリア外Mobのタイムアウト判定
        Iterator<Map.Entry<UUID, Long>> it = mobOutOfBoundsStart.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID mobId = entry.getKey();
            long startTime = entry.getValue();

            // トラッキングから除外済み（既に死亡等）なら除外
            if (session.getMobTeam(mobId) == null) {
                it.remove();
                continue;
            }

            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null || entity.isDead()) {
                it.remove();
                continue;
            }

            // エリア内に戻っているか再チェック
            if (field.contains(entity.getLocation())) {
                it.remove();
                continue;
            }

            long elapsed = now - startTime;
            if (elapsed >= graceMs) {
                // タイムアウト → Kill
                it.remove();

                String team = session.getMobTeam(mobId);
                ChatColor teamColor = team != null
                        ? session.getTeamColor(team) : ChatColor.WHITE;

                Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + team
                        + ChatColor.GRAY + " の " + ChatColor.WHITE + entity.getName()
                        + ChatColor.RED + " が戦闘エリア外で失格になりました！");

                // LivingEntity を Kill（EntityDeathEvent が発火し onMobDeath が呼ばれる）
                ((LivingEntity) entity).setHealth(0);
            }
        }
    }
}
