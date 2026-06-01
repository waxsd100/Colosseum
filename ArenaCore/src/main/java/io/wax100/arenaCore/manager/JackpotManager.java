package io.wax100.arenaCore.manager;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ジャックポット積立・発動・永続化を管理するマネージャ。
 *
 * <p>以下の資金がジャックポットに積立される:
 * <ul>
 *   <li>運営手数料（ベッタープールの一定%）</li>
 *   <li>端数（切り捨て分）</li>
 *   <li>勝利ベッター0人時の観客配当プール</li>
 *   <li>entry-fee（通常参加費）</li>
 * </ul>
 *
 * <p>発動条件: 勝利チームのベット比率が閾値未満（大穴勝利）の場合、
 * 積立金を全額引き出して勝利ベッターに分配する。
 *
 * <p>データは {@code plugins/ArenaCore/jackpot.yml} に永続化される。
 */
public class JackpotManager {

    private long balance = 0;
    private final File dataFile;
    private final Logger logger;

    /**
     * @param dataFolder プラグインのデータフォルダ（plugins/ArenaCore）
     * @param logger     ロガー
     */
    public JackpotManager(File dataFolder, Logger logger) {
        this.dataFile = new File(dataFolder, "jackpot.yml");
        this.logger = logger;
        load();
    }

    /**
     * 指定額をジャックポットに積立する。
     *
     * @param amount 積立額（0以上）
     */
    public void deposit(long amount) {
        if (amount <= 0) return;
        balance = Math.addExact(balance, amount);
        save();
    }

    /**
     * ジャックポット発動条件をチェックする。
     *
     * <p>勝利チームのベット比率が閾値未満の場合に発動（大穴勝利）。
     * 総ベット額が 0 の場合は発動しない。
     *
     * @param winnerTeamBets 勝利チームへのベット額合計
     * @param totalBets      全ベット額合計
     * @param threshold      発動閾値（例: 0.10 = 10%未満で発動）
     * @return 発動すべき場合 {@code true}
     */
    public boolean shouldTrigger(long winnerTeamBets, long totalBets, double threshold) {
        if (totalBets == 0) return false;
        if (balance <= 0) return false;
        return (double) winnerTeamBets / totalBets < threshold;
    }

    /**
     * ジャックポット残高を全額引き出してリセットする。
     *
     * @return 引き出し額
     */
    public long withdrawAll() {
        long amount = balance;
        balance = 0;
        save();
        return amount;
    }

    /**
     * 現在のジャックポット残高を返す。
     *
     * @return 残高
     */
    public long getBalance() {
        return balance;
    }

    /**
     * ジャックポット残高を YAML ファイルに永続化する。
     */
    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("balance", balance);
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "ジャックポットデータの保存に失敗しました", e);
        }
    }

    /**
     * YAML ファイルからジャックポット残高を読み込む。
     *
     * <p>ファイルが存在しない場合は残高 0 で初期化される。
     */
    public void load() {
        if (!dataFile.exists()) {
            balance = 0;
            return;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            balance = config.getLong("balance", 0);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ジャックポットデータの読み込みに失敗しました", e);
            balance = 0;
        }
    }
}
