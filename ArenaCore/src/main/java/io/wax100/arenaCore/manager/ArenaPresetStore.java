package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.BettingRegion;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.YamlHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * アリーナプリセットの YAML 永続化を担当する。
 *
 * <p>保存先は {@code plugins/ArenaCore/arenas/<名前>.yml}。
 * チーム構成・待機場・TP先・賭けエリア・戦闘エリアを保持し、
 * {@link PresetData} として不変データクラスで返却する。
 */
public class ArenaPresetStore {

    private final File arenasDir;
    private final Logger logger;

    /**
     * @param dataFolder プラグインのデータフォルダ（null不可）
     * @param logger     ロガー（null不可）
     */
    public ArenaPresetStore(File dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.arenasDir = new File(dataFolder, "arenas");
        arenasDir.mkdirs();
    }

    // ══════════════════════════════════════
    //  保存
    // ══════════════════════════════════════

    /**
     * セッションの現在の設定をYAMLファイルに保存する。
     *
     * @param name          プリセット名（ファイル名に使用、null不可）
     * @param session       保存するセッション（null不可）
     * @param regionManager 賭けエリア取得用（null不可）
     */
    public void save(String name, ArenaSession session, RegionManager regionManager) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(regionManager, "regionManager must not be null");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("teams", session.getTeamNames());

        // mob-teams
        List<String> mobTeamList = new ArrayList<>();
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) {
                mobTeamList.add(team);
            }
        }
        yaml.set("mob-teams", mobTeamList);

        // field
        ArenaFieldConfig fieldConfig = session.getFieldConfig();
        if (fieldConfig != null) {
            fieldConfig.toYaml(yaml, "field");
        }

        // team-areas
        for (String team : session.getTeamNames()) {
            TeamAreaConfig config = session.getTeamAreaConfig(team);
            if (config == null) continue;

            config.toYaml(yaml, "team-areas." + team);
        }

        // betting-regions
        for (String team : session.getTeamNames()) {
            if (!regionManager.hasBettingRegion(team)) continue;
            BettingRegion region = regionManager.getBettingRegion(team);
            if (region == null) continue;

            String basePath = "betting-regions." + team;
            yaml.set(basePath + ".world", region.worldName());
            yaml.set(basePath + ".min", List.of(region.minX(), region.minY(), region.minZ()));
            yaml.set(basePath + ".max", List.of(region.maxX(), region.maxY(), region.maxZ()));
        }

        // team-colors
        Map<String, ChatColor> teamColors = session.getTeamColors();
        if (!teamColors.isEmpty()) {
            for (Map.Entry<String, ChatColor> entry : teamColors.entrySet()) {
                yaml.set("team-colors." + entry.getKey(), entry.getValue().name());
            }
        }

        File file = new File(arenasDir, name + ".yml");
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("プリセット保存失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════
    //  ロード
    // ══════════════════════════════════════

    /**
     * YAMLファイルからプリセットデータを読み込む。
     *
     * <p>{@link ArenaSession} の生成は行わない。
     * 呼び出し側（{@code ArenaManager.createFromPreset}）が担当する。
     *
     * @param name プリセット名（null不可）
     * @return プリセットデータ。ファイルが存在しない場合 {@code null}
     */
    public PresetData load(String name) {
        Objects.requireNonNull(name, "name must not be null");

        File file = new File(arenasDir, name + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String presetName = yaml.getString("name", name);
        List<String> teamNames = yaml.getStringList("teams");

        // mob-teams: List → Set
        List<String> mobTeamList = yaml.getStringList("mob-teams");
        Set<String> mobTeams = new HashSet<>(mobTeamList);

        // field (nullable)
        ArenaFieldConfig fieldConfig = null;
        ConfigurationSection fieldSec = yaml.getConfigurationSection("field");
        if (fieldSec != null) {
            fieldConfig = ArenaFieldConfig.fromYaml(fieldSec);
        }

        // team-areas
        Map<String, TeamAreaConfig> teamAreaConfigs = new LinkedHashMap<>();
        ConfigurationSection areasSec = yaml.getConfigurationSection("team-areas");
        if (areasSec != null) {
            for (String team : areasSec.getKeys(false)) {
                ConfigurationSection teamSec = areasSec.getConfigurationSection(team);
                if (teamSec == null) continue;

                TeamAreaConfig config = TeamAreaConfig.fromYaml(teamSec);
                if (config == null) continue;

                teamAreaConfigs.put(team, config);
            }
        }

        // betting-regions
        Map<String, BettingRegion> bettingRegions = new LinkedHashMap<>();
        ConfigurationSection betSec = yaml.getConfigurationSection("betting-regions");
        if (betSec != null) {
            for (String team : betSec.getKeys(false)) {
                ConfigurationSection regionSec = betSec.getConfigurationSection(team);
                if (regionSec == null) continue;

                String worldName = regionSec.getString("world");
                List<Integer> min = regionSec.getIntegerList("min");
                List<Integer> max = regionSec.getIntegerList("max");
                if (worldName == null || min.size() != 3 || max.size() != 3) continue;

                bettingRegions.put(team, BettingRegion.of(team, worldName,
                        min.get(0), min.get(1), min.get(2),
                        max.get(0), max.get(1), max.get(2)));
            }
        }

        // team-colors
        Map<String, ChatColor> teamColors = new LinkedHashMap<>();
        ConfigurationSection colorSec = yaml.getConfigurationSection("team-colors");
        if (colorSec != null) {
            for (String team : colorSec.getKeys(false)) {
                try {
                    ChatColor color = ChatColor.valueOf(colorSec.getString(team));
                    teamColors.put(team, color);
                } catch (IllegalArgumentException ignored) {
                    // 無効な色名はスキップ
                }
            }
        }

        return new PresetData(presetName, teamNames, mobTeams,
                fieldConfig, teamAreaConfigs, bettingRegions, teamColors);
    }

    // ══════════════════════════════════════
    //  一覧
    // ══════════════════════════════════════

    /**
     * 保存済みプリセット名の一覧を返す。
     *
     * @return プリセット名リスト（拡張子除去済み）
     */
    public List<String> list() {
        return YamlHelper.listYmlNames(arenasDir);
    }

    // ══════════════════════════════════════
    //  削除
    // ══════════════════════════════════════

    /**
     * プリセットを削除する（{@code .yml} と {@code .schem} の両方）。
     *
     * @param name プリセット名（null不可）
     * @return {@code .yml} の削除に成功した場合 {@code true}
     */
    public boolean delete(String name) {
        Objects.requireNonNull(name, "name must not be null");
        boolean yml = new File(arenasDir, name + ".yml").delete();
        // .schem も同時削除（存在しなくてもエラーにしない）
        new File(arenasDir, name + ".schem").delete();
        return yml;
    }

    // ══════════════════════════════════════
    //  PresetData（不変データクラス）
    // ══════════════════════════════════════

    /**
     * プリセットの不変データを保持するレコード。
     *
     * <p>{@link ArenaSession} を直接生成せず、データのみを保持する。
     * セッション生成は {@code ArenaManager.createFromPreset()} が担当する。
     *
     * @param name            プリセット名
     * @param teamNames       チーム名リスト
     * @param mobTeams        モンスターチーム名セット
     * @param fieldConfig     戦闘エリア設定（nullable）
     * @param teamAreaConfigs チーム別待機場設定
     * @param bettingRegions  チーム別賭けエリア設定
     * @param teamColors      チーム別カラー設定
     */
    public record PresetData(
            String name,
            List<String> teamNames,
            Set<String> mobTeams,
            ArenaFieldConfig fieldConfig,
            Map<String, TeamAreaConfig> teamAreaConfigs,
            Map<String, BettingRegion> bettingRegions,
            Map<String, ChatColor> teamColors
    ) {}
}
