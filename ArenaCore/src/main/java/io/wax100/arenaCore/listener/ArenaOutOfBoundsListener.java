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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.EntityEffect;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
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
    /** エリア外警告済みMob（重複ブロードキャスト防止用） */
    private final Set<UUID> mobOobWarned = new HashSet<>();
    /** エリア外ダメージソースとしてマーク中のプレイヤー（damage→death判定用） */
    private final Set<UUID> oobDamaging = new HashSet<>();

    private final ArenaCore plugin;
    private BukkitTask countdownTask;
    /** 戦闘エリア境界線パーティクル描画タスク */
    private BukkitTask boundaryParticleTask;

    /** 境界線パーティクルの色（赤） */
    private static final Particle.DustOptions BOUNDARY_DUST =
            new Particle.DustOptions(Color.RED, 1.2F);
    /** 境界線パーティクルの間隔（ブロック） */
    private static final double PARTICLE_STEP = 0.5;

    public ArenaOutOfBoundsListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤー移動時にエリア外判定を行う。
     *
     * <p>ブロック移動がない場合（視点変更のみ）は無視する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
            if (!outOfBoundsStart.containsKey(playerId)) {
                outOfBoundsStart.put(playerId, System.currentTimeMillis());
                // 外に出た瞬間にダメージエフェクト（画面赤フラッシュ）
                player.playEffect(EntityEffect.HURT);
            }
        }
    }

    /**
     * カウントダウンタスクを開始する。
     *
     * <p>試合開始時に {@link ArenaManager} から呼び出される。
     * 1秒ごとにエリア外プレイヤー・モンスターの残り時間をチェックし、
     * 猶予時間超過後は毎秒ダメージを与えて徐々にKillする。
     */
    public void startCountdown() {
        stopCountdown();
        outOfBoundsStart.clear();
        mobOutOfBoundsStart.clear();
        mobOobWarned.clear();
        oobDamaging.clear();

        int gracePeriod = plugin.getConfig().getInt("out-of-bounds.grace-seconds", 10);
        int mobGracePeriod = plugin.getConfig().getInt("out-of-bounds.mob-grace-seconds", gracePeriod);
        double damagePercent = plugin.getConfig().getDouble("out-of-bounds.damage-percent-per-second", 10.0);

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
            checkPlayersOutOfBounds(manager, session, field, now, gracePeriod, damagePercent);

            // ── モンスターのエリア外チェック ──
            checkMobsOutOfBounds(manager, session, field, now, mobGracePeriod, damagePercent);

        }, 0L, 20L); // 1秒ごと

        // 戦闘エリア境界線のパーティクル描画を開始
        startBoundaryParticles();
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
        stopBoundaryParticles();
        outOfBoundsStart.clear();
        mobOutOfBoundsStart.clear();
        mobOobWarned.clear();
        oobDamaging.clear();
    }

    /**
     * プレイヤーのエリア外チェックを行う。
     *
     * <p>猶予時間内はカウントダウン警告を表示し、
     * 猶予時間超過後は毎秒ダメージを与えて徐々にKillする。
     * プレイヤーがダメージで死亡すると {@code PlayerDeathEvent} が発火し、
     * {@link io.wax100.arenaCore.listener.ArenaFightListener} が脱落処理を行う。
     *
     * @param manager       アリーナマネージャー
     * @param session       アクティブセッション
     * @param field         戦闘フィールド設定
     * @param now           現在時刻（ミリ秒）
     * @param gracePeriod   猶予秒数
     * @param damagePercent 猶予超過後に毎秒与えるダメージ（最大HPに対する%）
     */
    private void checkPlayersOutOfBounds(ArenaManager manager, ArenaSession session,
                                         ArenaFieldConfig field, long now,
                                         int gracePeriod, double damagePercent) {
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

            if (elapsed >= graceMs) {
                // ダメージフェーズ: 毎秒、最大HPの一定割合のダメージをdamage()で与える
                double damage = Math.max(1.0, player.getMaxHealth() * (damagePercent / 100.0));
                oobDamaging.add(playerId);
                player.damage(damage);
                oobDamaging.remove(playerId);

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(
                                ChatColor.RED + "⚠ 戦闘エリア外！ ダメージを受けています！"));
            } else {
                // カウントダウン警告（アクションバー）+ 毎秒ダメージエフェクト（画面赤フラッシュ）
                player.playEffect(EntityEffect.HURT);
                int remainingSeconds = (int) Math.ceil((graceMs - elapsed) / 1000.0);
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
     * <p>フィールド外にいるMobを検出し、猶予時間超過後は
     * 毎秒ダメージを与えて徐々にKillする。
     * エリア内に戻った場合は追跡を解除する。
     * Mobがダメージで死亡すると {@code EntityDeathEvent} が発火し、
     * {@link io.wax100.arenaCore.listener.ArenaFightListener} がチーム全滅判定を行う。
     *
     * @param manager        アリーナマネージャー
     * @param session        アクティブセッション
     * @param field          戦闘フィールド設定
     * @param now            現在時刻（ミリ秒）
     * @param mobGracePeriod Mob用猶予秒数
     * @param damagePercent  猶予超過後に毎秒与えるダメージ（最大HPに対する%）
     */
    private void checkMobsOutOfBounds(ArenaManager manager, ArenaSession session,
                                      ArenaFieldConfig field, long now,
                                      int mobGracePeriod, double damagePercent) {
        long graceMs = mobGracePeriod * 1000L;

        // 1. トラッキング中の全Mobを走査し、エリア外のMobを記録
        //    スナップショットを取得してイテレート（damage→即死→onMobDeath→removeMobによるConcurrentModificationException対策）
        for (Map.Entry<UUID, String> mobEntry : new ArrayList<>(session.getTrackedMobs().entrySet())) {
            UUID mobId = mobEntry.getKey();
            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null || entity.isDead() || !(entity instanceof LivingEntity)) {
                continue;
            }

            if (field.contains(entity.getLocation())) {
                // エリア内に戻った → 追跡解除
                mobOutOfBoundsStart.remove(mobId);
                mobOobWarned.remove(mobId);
            } else {
                // エリア外 → 初回記録
                mobOutOfBoundsStart.putIfAbsent(mobId, now);
            }
        }

        // 2. エリア外Mobのチェック
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
            if (elapsed >= graceMs && entity instanceof LivingEntity living) {
                // 初回ダメージ時にブロードキャスト
                if (mobOobWarned.add(mobId)) {
                    String team = session.getMobTeam(mobId);
                    ChatColor teamColor = team != null
                            ? session.getTeamColor(team) : ChatColor.WHITE;
                    Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + team
                            + ChatColor.GRAY + " の " + ChatColor.WHITE + entity.getName()
                            + ChatColor.RED + " が戦闘エリア外でダメージを受けています！");
                }

                // ダメージフェーズ: 毎秒、最大HPの一定割合のダメージをdamage()で与える
                double damage = Math.max(1.0, living.getMaxHealth() * (damagePercent / 100.0));
                living.damage(damage);
            }
        }
    }

    /**
     * エリア外ダメージ中かどうかを返す。
     *
     * <p>{@link ArenaFightListener} 等で、エリア外ダメージと通常戦闘を
     * 区別する必要がある場合に使用できる。
     *
     * @param playerId プレイヤーのUUID
     * @return エリア外ダメージ処理中の場合 {@code true}
     */
    public boolean isOobDamaging(UUID playerId) {
        return oobDamaging.contains(playerId);
    }

    // ══════════════════════════════════════
    //  戦闘エリア境界線パーティクル描画
    // ══════════════════════════════════════

    /**
     * 戦闘エリアの境界線を赤いパーティクルで描画するタスクを開始する。
     *
     * <p>試合中（ACTIVE状態）にフィールド範囲の12辺を赤いダストパーティクルで
     * 描画し、プレイヤーが範囲外を視覚的に把握できるようにする。
     * 1秒ごとに更新。
     */
    private void startBoundaryParticles() {
        stopBoundaryParticles();

        boundaryParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ArenaManager manager = plugin.getArenaManager();
            if (!manager.hasActiveSession()
                    || manager.getActiveSession().getState() != ArenaState.ACTIVE) {
                stopBoundaryParticles();
                return;
            }

            ArenaSession session = manager.getActiveSession();
            ArenaFieldConfig field = session.getFieldConfig();
            if (field == null) return;

            World world = field.getWorld();
            if (world == null) return;

            drawFieldBoundaryEdges(world, field);
        }, 0L, 20L); // 1秒ごと
    }

    /**
     * 境界線パーティクル描画タスクを停止する。
     */
    private void stopBoundaryParticles() {
        if (boundaryParticleTask != null) {
            boundaryParticleTask.cancel();
            boundaryParticleTask = null;
        }
    }

    /**
     * 戦闘エリアの12辺を赤いパーティクルで描画する。
     *
     * @param world ワールド
     * @param field 戦闘エリア設定
     */
    private void drawFieldBoundaryEdges(World world, ArenaFieldConfig field) {
        double x1 = field.minX();
        double y1 = field.minY();
        double z1 = field.minZ();
        double x2 = field.maxX() + 1.0;
        double y2 = field.maxY() + 1.0;
        double z2 = field.maxZ() + 1.0;

        // X軸に平行な4辺
        for (double x = x1; x <= x2; x += PARTICLE_STEP) {
            world.spawnParticle(Particle.REDSTONE, x, y1, z1, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x, y1, z2, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x, y2, z1, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x, y2, z2, 1, BOUNDARY_DUST);
        }
        // Z軸に平行な4辺
        for (double z = z1; z <= z2; z += PARTICLE_STEP) {
            world.spawnParticle(Particle.REDSTONE, x1, y1, z, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x2, y1, z, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x1, y2, z, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x2, y2, z, 1, BOUNDARY_DUST);
        }
        // Y軸に平行な4辺
        for (double y = y1; y <= y2; y += PARTICLE_STEP) {
            world.spawnParticle(Particle.REDSTONE, x1, y, z1, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x2, y, z1, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x1, y, z2, 1, BOUNDARY_DUST);
            world.spawnParticle(Particle.REDSTONE, x2, y, z2, 1, BOUNDARY_DUST);
        }
    }
}
