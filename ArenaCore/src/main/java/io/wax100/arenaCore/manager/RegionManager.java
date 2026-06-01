package io.wax100.arenaCore.manager;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import io.wax100.arenaCore.model.BettingRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;


/**
 * WorldEdit 連携によるエリア管理。
 *
 * <p>WorldEdit が存在する場合のみ動作し、プレイヤーの選択範囲を
 * チームベットエリアやチーム配属エリアとして使用する。
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
     * プレイヤーの WorldEdit 選択範囲をチームのベットエリアとして設定する。
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

            @SuppressWarnings("deprecation")
            BettingRegion bettingRegion = BettingRegion.of(teamName, worldName,
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ());

            bettingRegions.put(teamName, bettingRegion);
            return true;

        } catch (IncompleteRegionException e) {
            return false;
        }
    }

    /**
     * 指定座標がどのチームのベットエリア内にあるかを判定する。
     *
     * @param location 判定対象座標
     * @return チーム名。どのエリアにも含まれない場合 {@code null}
     */
    public String getTeamForLocation(Location location) {
        for (BettingRegion region : bettingRegions.values()) {
            if (region.contains(location)) {
                return region.teamName();
            }
        }
        return null;
    }

    /**
     * チームのベットエリアが設定されているかを返す。
     *
     * @param teamName チーム名
     * @return 設定されている場合 {@code true}
     */
    public boolean hasBettingRegion(String teamName) {
        return bettingRegions.containsKey(teamName);
    }

    /**
     * チームのベットエリアを取得する。
     *
     * @param teamName チーム名
     * @return ベットエリア。未設定の場合 {@code null}
     */
    public BettingRegion getBettingRegion(String teamName) {
        return bettingRegions.get(teamName);
    }



    /**
     * プリセットロード時にBettingRegionを直接登録する。
     *
     * @param teamName チーム名
     * @param region   ベットエリア
     */
    public void registerBettingRegion(String teamName, BettingRegion region) {
        bettingRegions.put(teamName, region);
    }

    /**
     * ベットエリアが1つでも設定されているかを返す。
     *
     * @return 設定されている場合 {@code true}
     */
    public boolean hasAnyRegion() {
        return !bettingRegions.isEmpty();
    }

    /**
     * 全ベットエリアをクリアする。
     */
    public void clearRegions() {
        bettingRegions.clear();
    }
}
