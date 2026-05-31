package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.model.TeamAreaConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 待機場（{@link TeamAreaConfig}）の YAML 永続化を担当する。
 *
 * <p>保存先は {@code plugins/ArenaCore/areas/<名前>.yml}。
 * 座標範囲（world, min, max）と転送先（destination）を保持する。
 */
public class AreaStore {

    private final File areasDir;
    private final Logger logger;

    /**
     * @param dataFolder プラグインのデータフォルダ（null不可）
     * @param logger     ロガー（null不可）
     */
    public AreaStore(File dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.areasDir = new File(dataFolder, "areas");
        areasDir.mkdirs();
    }

    // ══════════════════════════════════════
    //  保存
    // ══════════════════════════════════════

    /**
     * 待機場をYAMLファイルに保存する。
     *
     * @param name   待機場名（ファイル名に使用、null不可）
     * @param config 保存する待機場設定（null不可）
     */
    public void save(String name, TeamAreaConfig config) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(config, "config must not be null");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("world", config.worldName());
        yaml.set("min", List.of(config.minX(), config.minY(), config.minZ()));
        yaml.set("max", List.of(config.maxX(), config.maxY(), config.maxZ()));

        // destination (nullable)
        Location dest = config.getDestination();
        if (dest != null && dest.getWorld() != null) {
            yaml.set("destination.world", dest.getWorld().getName());
            yaml.set("destination.x", dest.getX());
            yaml.set("destination.y", dest.getY());
            yaml.set("destination.z", dest.getZ());
            yaml.set("destination.yaw", (double) dest.getYaw());
            yaml.set("destination.pitch", (double) dest.getPitch());
        }

        File file = new File(areasDir, name + ".yml");
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("待機場保存失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════
    //  ロード
    // ══════════════════════════════════════

    /**
     * YAMLファイルから待機場を読み込む。
     *
     * @param name 待機場名（null不可）
     * @return 待機場設定。ファイルが存在しない場合 {@code null}
     */
    public TeamAreaConfig load(String name) {
        Objects.requireNonNull(name, "name must not be null");

        File file = new File(areasDir, name + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String worldName = yaml.getString("world");
        List<Integer> min = yaml.getIntegerList("min");
        List<Integer> max = yaml.getIntegerList("max");
        if (worldName == null || min.size() != 3 || max.size() != 3) return null;

        TeamAreaConfig config = new TeamAreaConfig(worldName,
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));

        // destination (nullable)
        ConfigurationSection destSec = yaml.getConfigurationSection("destination");
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

    // ══════════════════════════════════════
    //  削除
    // ══════════════════════════════════════

    /**
     * 保存済み待機場を削除する。
     *
     * @param name 待機場名（null不可）
     * @return 削除に成功した場合 {@code true}
     */
    public boolean delete(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return new File(areasDir, name + ".yml").delete();
    }

    // ══════════════════════════════════════
    //  一覧
    // ══════════════════════════════════════

    /**
     * 保存済み待機場名の一覧を返す。
     *
     * @return 待機場名リスト（拡張子除去済み）
     */
    public List<String> list() {
        File[] files = areasDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        List<String> names = new ArrayList<>();
        for (File file : files) {
            String fileName = file.getName();
            names.add(fileName.substring(0, fileName.length() - 4));
        }
        Collections.sort(names);
        return names;
    }

    // ══════════════════════════════════════
    //  存在確認
    // ══════════════════════════════════════

    /**
     * 指定名の待機場が保存済みかどうかを返す。
     *
     * @param name 待機場名（null不可）
     * @return 存在する場合 {@code true}
     */
    public boolean exists(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return new File(areasDir, name + ".yml").exists();
    }
}
