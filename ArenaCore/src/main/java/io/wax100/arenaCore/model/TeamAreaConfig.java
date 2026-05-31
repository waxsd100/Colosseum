package io.wax100.arenaCore.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * チームの待機エリアとTP先を保持する設定クラス。
 *
 * <p>プレイヤーチーム・モンスターチームの両方で使用する。
 * 管理者が事前にプレイヤーやMobを待機エリアに配置し、
 * 試合開始時にTP先へ転送する。
 */
public class TeamAreaConfig implements CuboidArea {

    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private Location destination;

    /**
     * 待機エリアを指定して作成する。
     *
     * @param worldName ワールド名（null不可）
     * @param x1        第1点 X
     * @param y1        第1点 Y
     * @param z1        第1点 Z
     * @param x2        第2点 X
     * @param y2        第2点 Y
     * @param z2        第2点 Z
     * @throws NullPointerException worldName が null の場合
     */
    public TeamAreaConfig(String worldName,
                          int x1, int y1, int z1,
                          int x2, int y2, int z2) {
        this.worldName = Objects.requireNonNull(worldName, "worldName must not be null");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * 待機エリア内の全 LivingEntity（Player を除く）を取得する。
     *
     * @return エリア内のMobリスト
     */
    public List<LivingEntity> scanEntities() {
        List<LivingEntity> result = new ArrayList<>();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return result;

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (contains(entity.getLocation())) {
                result.add((LivingEntity) entity);
            }
        }
        return result;
    }

    /**
     * 待機エリア内の全 Player を取得する。
     *
     * @return エリア内のプレイヤーリスト
     */
    public List<Player> scanPlayers() {
        List<Player> result = new ArrayList<>();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return result;

        for (Player player : world.getPlayers()) {
            if (contains(player.getLocation())) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * YAML セクションから {@link TeamAreaConfig} を復元する。
     *
     * <p>セクションには {@code world}, {@code min}, {@code max} キーが必要。
     * {@code destination} セクションは任意。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定。データ不正の場合は {@code null}
     */
    public static TeamAreaConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        String worldName = section.getString("world");
        List<Integer> min = section.getIntegerList("min");
        List<Integer> max = section.getIntegerList("max");
        if (worldName == null || min.size() != 3 || max.size() != 3) return null;

        TeamAreaConfig config = new TeamAreaConfig(worldName,
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));

        // destination (nullable)
        ConfigurationSection destSec = section.getConfigurationSection("destination");
        if (destSec != null) {
            String destWorld = destSec.getString("world");
            if (destWorld != null) {
                World world = Bukkit.getWorld(destWorld);
                if (world != null) {
                    double x = destSec.getDouble("x");
                    double y = destSec.getDouble("y");
                    double z = destSec.getDouble("z");
                    float yaw = (float) destSec.getDouble("yaw", 0.0);
                    float pitch = (float) destSec.getDouble("pitch", 0.0);
                    config.setDestination(new Location(world, x, y, z, yaw, pitch));
                }
            }
        }

        return config;
    }

    /**
     * この設定を YAML に書き出す。
     *
     * @param yaml     書き出し先の YamlConfiguration（null不可）
     * @param basePath キーの接頭辞（例: {@code "team-areas.red"}）
     */
    public void toYaml(YamlConfiguration yaml, String basePath) {
        Objects.requireNonNull(yaml, "yaml must not be null");
        Objects.requireNonNull(basePath, "basePath must not be null");
        String prefix = basePath.isEmpty() ? "" : basePath + ".";
        yaml.set(prefix + "world", worldName);
        yaml.set(prefix + "min", List.of(minX, minY, minZ));
        yaml.set(prefix + "max", List.of(maxX, maxY, maxZ));

        Location dest = getDestination();
        if (dest != null && dest.getWorld() != null) {
            yaml.set(prefix + "destination.world", dest.getWorld().getName());
            yaml.set(prefix + "destination.x", dest.getX());
            yaml.set(prefix + "destination.y", dest.getY());
            yaml.set(prefix + "destination.z", dest.getZ());
            yaml.set(prefix + "destination.yaw", (double) dest.getYaw());
            yaml.set(prefix + "destination.pitch", (double) dest.getPitch());
        }
    }

    /**
     * 転送先座標の防御コピーを返す。
     *
     * @return 転送先座標のコピー。未設定の場合は {@code null}
     */
    public Location getDestination() {
        return destination != null ? destination.clone() : null;
    }

    /**
     * 転送先を設定する（防御コピーを保持）。
     *
     * @param destination 転送先座標（null許容）
     */
    public void setDestination(Location destination) {
        this.destination = destination != null ? destination.clone() : null;
    }

    public String worldName() { return worldName; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamAreaConfig that)) return false;
        return minX == that.minX && minY == that.minY && minZ == that.minZ
                && maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ
                && worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        String destStr;
        if (destination == null) {
            destStr = "null";
        } else {
            World w = destination.getWorld();
            destStr = (w != null ? w.getName() : "?") + "("
                    + destination.getX() + "," + destination.getY() + ","
                    + destination.getZ() + ")";
        }
        return "TeamAreaConfig{" +
                "world='" + worldName + '\'' +
                ", min=(" + minX + "," + minY + "," + minZ + ")" +
                ", max=(" + maxX + "," + maxY + "," + maxZ + ")" +
                ", destination=" + destStr +
                '}';
    }
}
