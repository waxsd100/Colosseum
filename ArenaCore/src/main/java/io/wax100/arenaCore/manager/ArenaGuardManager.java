package io.wax100.arenaCore.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;

/**
 * WorldGuard によるアリーナ戦闘エリアの入退場制御。
 *
 * <p>試合開始時に戦闘エリアの WorldGuard リージョンを作成し、
 * 参加者のみ入場可能・退場不可に設定する。試合終了時にリージョンを削除する。
 *
 * <p>WorldGuard がサーバに存在しない場合は何もしない（ソフト依存）。
 */
public class ArenaGuardManager {

    private static final String REGION_PREFIX = "arenacore_field_";

    private final Plugin plugin;
    private final boolean available;

    private String activeRegionId;
    private String activeWorldName;

    public ArenaGuardManager(Plugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (!available) {
            plugin.getLogger().info("WorldGuard が見つかりません。入退場制御は無効です。");
        }
    }

    /**
     * WorldGuard が利用可能かどうかを返す。
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 試合開始時にアリーナフィールドをロックする。
     *
     * <p>フィールド範囲に WorldGuard リージョンを作成し、
     * 参加ファイター以外の entry を DENY、参加者の exit を DENY にする。
     *
     * @param session 現在のアリーナセッション
     */
    public void lockField(ArenaSession session) {
        if (!available) return;

        ArenaFieldConfig field = session.getFieldConfig();
        if (field == null) return;

        World world = field.getWorld();
        if (world == null) return;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) return;

        String regionId = REGION_PREFIX + session.getName().toLowerCase();

        // 既存リージョンがあれば削除
        if (regionManager.hasRegion(regionId)) {
            regionManager.removeRegion(regionId);
        }

        // フィールド範囲でリージョン作成
        BlockVector3 min = field.toRegion().getMinimumPoint();
        BlockVector3 max = field.toRegion().getMaximumPoint();
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

        // 優先度を高く設定（他リージョンより優先）
        region.setPriority(100);

        // 非メンバーの入場を拒否
        region.setFlag(Flags.ENTRY, StateFlag.State.DENY);
        // メンバーの退場を拒否
        region.setFlag(Flags.EXIT, StateFlag.State.DENY);

        // 全ファイターをメンバーとして追加（メンバーは entry DENY を無視できる）
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

    /**
     * 試合終了時にアリーナフィールドのロックを解除する。
     */
    public void unlockField() {
        if (!available) return;
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
}
