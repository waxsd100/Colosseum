package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.model.ArenaFieldConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 戦闘エリア（{@link ArenaFieldConfig}）の YAML 永続化を担当する。
 *
 * <p>保存先は {@code plugins/ArenaCore/fields/<名前>.yml}。
 * 座標範囲（world, min, max）を保持する。
 * Schematic ファイル（{@code .schem}）は同ディレクトリに配置されるが、
 * コピー処理自体は呼び出し側が担当する。
 */
public class FieldStore {

    private final File fieldsDir;
    private final Logger logger;

    /**
     * @param dataFolder プラグインのデータフォルダ（null不可）
     * @param logger     ロガー（null不可）
     */
    public FieldStore(File dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.fieldsDir = new File(dataFolder, "fields");
        fieldsDir.mkdirs();
    }

    // ══════════════════════════════════════
    //  保存
    // ══════════════════════════════════════

    /**
     * 戦闘エリアをYAMLファイルに保存する。
     *
     * @param name   戦闘エリア名（ファイル名に使用、null不可）
     * @param config 保存する戦闘エリア設定（null不可）
     */
    public void save(String name, ArenaFieldConfig config) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(config, "config must not be null");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("world", config.getWorldName());
        yaml.set("min", List.of(config.getMinX(), config.getMinY(), config.getMinZ()));
        yaml.set("max", List.of(config.getMaxX(), config.getMaxY(), config.getMaxZ()));

        File file = new File(fieldsDir, name + ".yml");
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("戦闘エリア保存失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════
    //  ロード
    // ══════════════════════════════════════

    /**
     * YAMLファイルから戦闘エリアを読み込む。
     *
     * @param name 戦闘エリア名（null不可）
     * @return 戦闘エリア設定。ファイルが存在しない場合 {@code null}
     */
    public ArenaFieldConfig load(String name) {
        Objects.requireNonNull(name, "name must not be null");

        File file = new File(fieldsDir, name + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String worldName = yaml.getString("world");
        List<Integer> min = yaml.getIntegerList("min");
        List<Integer> max = yaml.getIntegerList("max");
        if (worldName == null || min.size() != 3 || max.size() != 3) return null;

        return new ArenaFieldConfig(worldName,
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));
    }

    // ══════════════════════════════════════
    //  削除
    // ══════════════════════════════════════

    /**
     * 保存済み戦闘エリアを削除する（{@code .yml} と {@code .schem} の両方）。
     *
     * @param name 戦闘エリア名（null不可）
     * @return {@code .yml} の削除に成功した場合 {@code true}
     */
    public boolean delete(String name) {
        Objects.requireNonNull(name, "name must not be null");
        boolean yml = new File(fieldsDir, name + ".yml").delete();
        // .schem も同時削除（存在しなくてもエラーにしない）
        new File(fieldsDir, name + ".schem").delete();
        return yml;
    }

    // ══════════════════════════════════════
    //  一覧
    // ══════════════════════════════════════

    /**
     * 保存済み戦闘エリア名の一覧を返す。
     *
     * @return 戦闘エリア名リスト（拡張子除去済み）
     */
    public List<String> list() {
        File[] files = fieldsDir.listFiles((dir, name) -> name.endsWith(".yml"));
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
     * 指定名の戦闘エリアが保存済みかどうかを返す。
     *
     * @param name 戦闘エリア名（null不可）
     * @return 存在する場合 {@code true}
     */
    public boolean exists(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return new File(fieldsDir, name + ".yml").exists();
    }

    // ══════════════════════════════════════
    //  Schematic ファイル取得
    // ══════════════════════════════════════

    /**
     * Schematic ファイル（{@code .schem}）の File オブジェクトを返す。
     *
     * <p>ファイルが実際に存在するかは保証しない。
     * 呼び出し側が Schematic の保存・コピーに使用する。
     *
     * @param name 戦闘エリア名（null不可）
     * @return {@code fields/<名前>.schem} の File オブジェクト
     */
    public File getFieldFile(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return new File(fieldsDir, name + ".schem");
    }
}
