package io.wax100.arenaCore.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * WorldGuard によるアリーナ戦闘エリアの入退場制御 + Mob脱出防止。
 *
 * <p>試合開始時に戦闘エリアの WorldGuard リージョンを作成し、
 * 参加者のみ入場可能・退場不可に設定する。試合終了時にリージョンを削除する。
 *
 * <p>Mobは WorldGuard の exit フラグが効かないため、
 * 定期タスクでフィールド範囲外のMobをフィールド中心へ引き戻す。
 *
 * <p>WorldGuard がサーバに存在しない場合でもMob脱出防止は動作する。
 */
public class ArenaGuardManager {

    private static final String REGION_PREFIX = "arenacore_field_";

    /** Mob位置チェック間隔（tick） */
    private static final long MOB_CHECK_INTERVAL = 10L;

    private final Plugin plugin;
    private final boolean worldGuardAvailable;

    private String activeRegionId;
    private String activeWorldName;
    private ArenaFieldConfig activeField;
    private ArenaSession activeSession;
    private BukkitTask mobBounceTask;

    /**
     * @param plugin ArenaCore プラグインインスタンス
     */
    public ArenaGuardManager(Plugin plugin) {
        this.plugin = plugin;
        this.worldGuardAvailable = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (!worldGuardAvailable) {
            plugin.getLogger().info("WorldGuard が見つかりません。プレイヤー入退場制御は無効です。");
        }
    }

    /**
     * WorldGuard が利用可能かどうかを返す。
     */
    public boolean isWorldGuardAvailable() {
        return worldGuardAvailable;
    }

    /**
     * 試合開始時にアリーナフィールドをロックする。
     *
     * @param session 現在のアリーナセッション
     */
    public void lockField(ArenaSession session) {
        ArenaFieldConfig field = session.getFieldConfig();
        if (field == null) return;

        this.activeField = field;
        this.activeSession = session;

        // WorldGuard リージョン作成（プレイヤー用）
        if (worldGuardAvailable) {
            lockWithWorldGuard(session, field);
        }

        // Mob脱出防止タスク開始（WorldGuard不要）
        startMobBounceTask();
    }

    /**
     * 試合終了時にアリーナフィールドのロックを解除する。
     */
    public void unlockField() {
        // Mob脱出防止タスク停止
        stopMobBounceTask();

        // WorldGuard リージョン削除
        if (worldGuardAvailable) {
            unlockWorldGuard();
        }

        activeField = null;
        activeSession = null;
    }

    // ══════════════════════════════════════
    //  WorldGuard（プレイヤー用）
    // ══════════════════════════════════════

    private void lockWithWorldGuard(ArenaSession session, ArenaFieldConfig field) {
        World world = field.getWorld();
        if (world == null) return;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) return;

        // WorldGuard は ASCII英数字・ハイフン・アンダースコアのみ許可
        String regionId = REGION_PREFIX + session.getName().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");

        // 既存リージョンがあれば削除
        if (regionManager.hasRegion(regionId)) {
            regionManager.removeRegion(regionId);
        }

        // フィールド範囲でリージョン作成
        com.sk89q.worldedit.regions.CuboidRegion cuboid = field.toRegion();
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

        // 優先度を高く設定
        region.setPriority(100);

        // 非メンバーの入場を拒否 / メンバーの退場を拒否
        region.setFlag(Flags.ENTRY, StateFlag.State.DENY);
        region.setFlag(Flags.EXIT, StateFlag.State.DENY);

        // 全ファイターをメンバーとして追加
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) continue;
            for (UUID playerId : session.getTeamMembers(team)) {
                region.getMembers().addPlayer(playerId);
            }
        }

        regionManager.addRegion(region);
        activeRegionId = regionId;
        activeWorldName = field.worldName();
        plugin.getLogger().info("WorldGuard: アリーナフィールドをロック (" + regionId + ")");
    }

    private void unlockWorldGuard() {
        if (activeRegionId == null || activeWorldName == null) return;

        World world = Bukkit.getWorld(activeWorldName);
        if (world == null) return;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) return;

        if (regionManager.hasRegion(activeRegionId)) {
            regionManager.removeRegion(activeRegionId);
            plugin.getLogger().info("WorldGuard: アリーナフィールドをアンロック (" + activeRegionId + ")");
        }

        activeRegionId = null;
        activeWorldName = null;
    }

    // ══════════════════════════════════════
    //  Mob脱出防止（Bukkit純正）
    // ══════════════════════════════════════

    private void startMobBounceTask() {
        if (mobBounceTask != null) return;

        mobBounceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeField == null || activeSession == null) return;

            World world = activeField.getWorld();
            if (world == null) return;

            // フィールド中心を計算
            Location center = activeField.getCenter(world);

            // 追跡中のMobをチェック
            for (Map.Entry<UUID, String> entry : activeSession.getTrackedMobs().entrySet()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity == null || entity.isDead()) continue;
                if (!entity.getWorld().equals(world)) continue;

                Location loc = entity.getLocation();
                if (!activeField.contains(loc)) {
                    // 範囲外 → フィールド中心方向へ引き戻し
                    Vector direction = center.toVector().subtract(loc.toVector());
                    if (direction.lengthSquared() < 0.001) continue;
                    entity.setVelocity(direction.normalize().multiply(0.8));
                }
            }
        }, MOB_CHECK_INTERVAL, MOB_CHECK_INTERVAL);
    }

    private void stopMobBounceTask() {
        if (mobBounceTask != null) {
            mobBounceTask.cancel();
            mobBounceTask = null;
        }
    }
}
