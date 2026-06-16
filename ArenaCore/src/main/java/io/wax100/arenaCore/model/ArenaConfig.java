package io.wax100.arenaCore.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

/**
 * 闘技場セッションごとのゲームルール設定。
 *
 * <p>グローバル {@code config.yml} の値をデフォルトとして使用し、
 * セッション単位で上書きできる。プリセットにも保存される。
 *
 * <h3>設定キー一覧</h3>
 * <ul>
 *   <li>{@code entry-fee} — 参加費（0 = 無料）</li>
 *   <li>{@code win-condition} — 勝利条件（{@code last-team-standing} / {@code manual} / {@code score}）</li>
 *   <li>{@code score-target} — スコア制の目標値</li>
 *   <li>{@code fighter-guarantee} — 勝敗に関わらず全闘技者に支払う保証金</li>
 * </ul>
 */
public class ArenaConfig {

    private long entryFee;
    private String winCondition;
    private int scoreTarget;
    private long fighterGuarantee;

    /**
     * グローバル設定からデフォルト値で初期化する。
     *
     * @param globalConfig グローバル config.yml（null不可）
     */
    public ArenaConfig(FileConfiguration globalConfig) {
        Objects.requireNonNull(globalConfig, "globalConfig must not be null");
        this.entryFee = globalConfig.getLong("entry-fee", 0);
        this.winCondition = globalConfig.getString("win-condition", "last-team-standing");
        this.scoreTarget = globalConfig.getInt("score-target", 0);
        this.fighterGuarantee = globalConfig.getLong("fighter-guarantee", 100);
    }

    /**
     * すべての値を明示的に指定して作成する。
     */
    public ArenaConfig(long entryFee, String winCondition, int scoreTarget, long fighterGuarantee) {
        this.entryFee = entryFee;
        this.winCondition = winCondition != null ? winCondition : "last-team-standing";
        this.scoreTarget = scoreTarget;
        this.fighterGuarantee = fighterGuarantee;
    }

    // ── Getters / Setters ──

    /** 参加費を返す。 */
    public long getEntryFee() { return entryFee; }
    /** 参加費を設定する。 */
    public void setEntryFee(long entryFee) { this.entryFee = Math.max(0, entryFee); }

    /** 勝利条件の種別文字列を返す。 */
    public String getWinCondition() { return winCondition; }
    /**
     * 勝利条件を設定する。
     *
     * @param winCondition {@code "last-team-standing"}, {@code "manual"}, {@code "score"} のいずれか
     * @return 有効な値が設定された場合 {@code true}
     */
    public boolean setWinCondition(String winCondition) {
        if (winCondition == null) return false;
        String lower = winCondition.toLowerCase();
        if (!lower.equals("last-team-standing") && !lower.equals("manual") && !lower.equals("score")) {
            return false;
        }
        this.winCondition = lower;
        return true;
    }

    /** スコア制の目標値を返す。 */
    public int getScoreTarget() { return scoreTarget; }
    /** スコア制の目標値を設定する。 */
    public void setScoreTarget(int scoreTarget) { this.scoreTarget = Math.max(0, scoreTarget); }

    /** 闘技者保証金を返す。 */
    public long getFighterGuarantee() { return fighterGuarantee; }
    /** 闘技者保証金を設定する。 */
    public void setFighterGuarantee(long fighterGuarantee) { this.fighterGuarantee = Math.max(0, fighterGuarantee); }

    // ── YAML シリアライズ ──

    /**
     * この設定を YAML セクションに書き出す。
     *
     * @param yaml     書き出し先
     * @param basePath キーの接頭辞（例: {@code "config"}）
     */
    public void toYaml(YamlConfiguration yaml, String basePath) {
        String prefix = basePath.isEmpty() ? "" : basePath + ".";
        yaml.set(prefix + "entry-fee", entryFee);
        yaml.set(prefix + "win-condition", winCondition);
        yaml.set(prefix + "score-target", scoreTarget);
        yaml.set(prefix + "fighter-guarantee", fighterGuarantee);
    }

    /**
     * YAML セクションから設定を復元する。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定
     */
    public static ArenaConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        long entryFee = section.getLong("entry-fee", 0);
        String winCondition = section.getString("win-condition", "last-team-standing");
        int scoreTarget = section.getInt("score-target", 0);
        long fighterGuarantee = section.getLong("fighter-guarantee", 100);
        return new ArenaConfig(entryFee, winCondition, scoreTarget, fighterGuarantee);
    }

    /**
     * 勝利条件の表示名を返す。
     *
     * @return 日本語表示名
     */
    public String getWinConditionDisplayName() {
        return switch (winCondition) {
            case "manual" -> "手動宣言";
            case "score" -> "スコア制 (目標: " + scoreTarget + ")";
            default -> "全滅方式";
        };
    }
}
