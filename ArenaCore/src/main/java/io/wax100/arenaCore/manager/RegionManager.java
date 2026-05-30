package io.wax100.arenaCore.manager;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import io.wax100.arenaCore.model.BettingRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WorldEdit 連携によるエリア管理。
 *
 * <p>WorldEdit が存在する場合のみ動作し、プレイヤーの選択範囲を
 * チーム賭けエリアやチーム配属エリアとして使用する。
 */
public class RegionManager {

    private final boolean worldEditAvailable;
    private final Map<String, BettingRegion> bettingRegions = new HashMap<>();

    /**
     * コンストラクタ。
     *
     * @param worldEditAvailable WorldEdit が利用可能かどうか
     */
    public RegionManager(boolean worldEditAvailable) {
        this.worldEditAvailable = worldEditAvailable;
    }

    /**
     * WorldEdit が利用可能かを返す。
     *
     * @return 利用可能な場合 {@code true}
     */
    public boolean isWorldEditAvailable() {
        return worldEditAvailable;
    }

    /**
     * プレイヤーの WorldEdit 選択範囲をチームの賭けエリアとして設定する。
     *
     * @param player   選択範囲を持つプレイヤー
     * @param teamName チーム名
     * @return 成功した場合 {@code true}
     */
    public boolean setBettingRegion(Player player, String teamName) {
        if (!worldEditAvailable) return false;

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            Region region = WorldEdit.getInstance().getSessionManager()
                    .get(wePlayer).getSelection(wePlayer.getWorld());

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            String worldName = player.getWorld().getName();

            BettingRegion bettingRegion = new BettingRegion(teamName, worldName,
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z());

            bettingRegions.put(teamName, bettingRegion);
            return true;

        } catch (IncompleteRegionException e) {
            return false;
        }
    }

    /**
     * 指定座標がどのチームの賭けエリア内にあるかを判定する。
     *
     * @param location 判定対象座標
     * @return チーム名。どのエリアにも含まれない場合 {@code null}
     */
    public String getTeamForLocation(Location location) {
        for (BettingRegion region : bettingRegions.values()) {
            if (region.contains(location)) {
                return region.getTeamName();
            }
        }
        return null;
    }

    /**
     * チームの賭けエリアが設定されているかを返す。
     *
     * @param teamName チーム名
     * @return 設定されている場合 {@code true}
     */
    public boolean hasBettingRegion(String teamName) {
        return bettingRegions.containsKey(teamName);
    }

    /**
     * チームの賭けエリアを取得する。
     *
     * @param teamName チーム名
     * @return 賭けエリア。未設定の場合 {@code null}
     */
    public BettingRegion getBettingRegion(String teamName) {
        return bettingRegions.get(teamName);
    }

    /**
     * プレイヤーの WorldEdit 選択範囲内にいるプレイヤーの UUID リストを返す。
     *
     * @param selector  選択範囲を持つプレイヤー（管理者）
     * @return エリア内のプレイヤー UUID リスト
     */
    public List<UUID> getPlayersInSelection(Player selector) {
        List<UUID> players = new ArrayList<>();
        if (!worldEditAvailable) return players;

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(selector);
            Region region = WorldEdit.getInstance().getSessionManager()
                    .get(wePlayer).getSelection(wePlayer.getWorld());

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            World world = selector.getWorld();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(world)) continue;
                Location loc = p.getLocation();
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (x >= min.x() && x <= max.x()
                        && y >= min.y() && y <= max.y()
                        && z >= min.z() && z <= max.z()) {
                    players.add(p.getUniqueId());
                }
            }

        } catch (IncompleteRegionException e) {
            // 選択範囲がない場合は空リスト
        }

        return players;
    }

    /**
     * プリセットロード時にBettingRegionを直接登録する。
     *
     * @param teamName チーム名
     * @param region   賭けエリア
     */
    public void registerBettingRegion(String teamName, BettingRegion region) {
        bettingRegions.put(teamName, region);
    }

    /**
     * 全賭けエリアをクリアする。
     */
    public void clearRegions() {
        bettingRegions.clear();
    }
}
