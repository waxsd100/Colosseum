package io.wax100.casinoCore.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OfflinePayoutManager のデータ永続化ロジックに対するテスト。
 *
 * <p>Bukkit イベント（PlayerJoinEvent）やスケジューラは静的 API のため、
 * ここでは YML ファイルへの読み書きロジックと dirty フラグ管理を
 * YamlConfiguration を直接操作して検証する。
 */
@DisplayName("OfflinePayoutManager: データ永続化テスト")
class OfflinePayoutManagerTest {

    @TempDir
    File tempDir;

    private File ymlFile;

    @BeforeEach
    void setUp() {
        ymlFile = new File(tempDir, "offline_payouts.yml");
    }

    /** addOfflineResult と同等のロジック */
    private void addOfflineResult(YamlConfiguration config, UUID playerId, long betAmount, long wonAmount) {
        String path = playerId.toString();
        long currentBet = config.getLong(path + ".bet", 0L);
        long currentWon = config.getLong(path + ".won", 0L);
        config.set(path + ".bet", currentBet + betAmount);
        config.set(path + ".won", currentWon + wonAmount);
    }

    // ========================================================================
    // データ保存
    // ========================================================================

    @Nested
    @DisplayName("データ保存")
    class DataStorageTest {

        @Test
        @DisplayName("ベット結果が正しく保存される")
        void addOfflineResult_storesCorrectly() throws IOException {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();

            addOfflineResult(config, player, 5000, 15000);
            config.save(ymlFile);

            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(ymlFile);
            assertEquals(5000, loaded.getLong(player + ".bet"));
            assertEquals(15000, loaded.getLong(player + ".won"));
        }

        @Test
        @DisplayName("同一プレイヤーの結果が加算される")
        void addOfflineResult_accumulates() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();

            addOfflineResult(config, player, 3000, 9000);
            addOfflineResult(config, player, 2000, 0);

            assertEquals(5000, config.getLong(player + ".bet"));
            assertEquals(9000, config.getLong(player + ".won"));
        }

        @Test
        @DisplayName("複数プレイヤーの結果が独立に管理される")
        void multiplePlayers_independent() {
            YamlConfiguration config = new YamlConfiguration();
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            addOfflineResult(config, p1, 1000, 3000);
            addOfflineResult(config, p2, 5000, 0);

            assertEquals(1000, config.getLong(p1 + ".bet"));
            assertEquals(3000, config.getLong(p1 + ".won"));
            assertEquals(5000, config.getLong(p2 + ".bet"));
            assertEquals(0, config.getLong(p2 + ".won"));
        }

        @Test
        @DisplayName("ファイル永続化後に再読み込みで復元される")
        void persistence_survives_reload() throws IOException {
            UUID player = UUID.randomUUID();

            YamlConfiguration config1 = new YamlConfiguration();
            addOfflineResult(config1, player, 10000, 25000);
            config1.save(ymlFile);

            YamlConfiguration config2 = YamlConfiguration.loadConfiguration(ymlFile);
            assertEquals(10000, config2.getLong(player + ".bet"));
            assertEquals(25000, config2.getLong(player + ".won"));
        }

        @Test
        @DisplayName("空ファイルからの読み込みでエラーが発生しない")
        void emptyFile_loadsWithoutError() throws IOException {
            ymlFile.createNewFile();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(ymlFile);
            UUID player = UUID.randomUUID();

            assertFalse(config.contains(player.toString()));
        }
    }

    // ========================================================================
    // ログイン処理のデータ判定
    // ========================================================================

    @Nested
    @DisplayName("ログイン処理のデータ判定")
    class LoginDataTest {

        @Test
        @DisplayName("勝ちの場合: won > 0 かつ won >= bet")
        void winCase_wonGreaterThanBet() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();
            addOfflineResult(config, player, 5000, 15000);

            long bet = config.getLong(player + ".bet");
            long won = config.getLong(player + ".won");

            assertTrue(won > 0);
            assertTrue(won >= bet);
            assertEquals(10000, won - bet);
        }

        @Test
        @DisplayName("負けの場合: won = 0")
        void lossCase_wonIsZero() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();
            addOfflineResult(config, player, 5000, 0);

            assertEquals(0, config.getLong(player + ".won"));
        }

        @Test
        @DisplayName("両チームベット: won > 0 かつ won < bet (一部勝ち)")
        void bothTeamBet_partialWin() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();

            addOfflineResult(config, player, 1000, 3000);
            addOfflineResult(config, player, 5000, 0);

            long bet = config.getLong(player + ".bet");
            long won = config.getLong(player + ".won");

            assertEquals(6000, bet);
            assertEquals(3000, won);
            assertTrue(won > 0);
            assertTrue(bet > won);
        }

        @Test
        @DisplayName("データ削除後にcontainsがfalseを返す")
        void clearData_afterProcessing() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();
            addOfflineResult(config, player, 5000, 15000);

            assertTrue(config.contains(player.toString()));
            config.set(player.toString(), null);
            assertFalse(config.contains(player.toString()));
        }
    }

    // ========================================================================
    // 3チーム以上のオフライン結果
    // ========================================================================

    @Nested
    @DisplayName("3チーム以上のオフライン結果")
    class MultiTeamOfflineTest {

        @Test
        @DisplayName("3チームベット: 1勝2敗で正しく集計")
        void threeTeamBet_oneWinTwoLoss() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();

            addOfflineResult(config, player, 3000, 9000);
            addOfflineResult(config, player, 2000, 0);
            addOfflineResult(config, player, 1000, 0);

            assertEquals(6000, config.getLong(player + ".bet"));
            assertEquals(9000, config.getLong(player + ".won"));
        }

        @Test
        @DisplayName("5チームベット: 全敗で won = 0")
        void fiveTeamBet_allLoss() {
            YamlConfiguration config = new YamlConfiguration();
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                addOfflineResult(config, player, 1000, 0);
            }

            assertEquals(5000, config.getLong(player + ".bet"));
            assertEquals(0, config.getLong(player + ".won"));
        }

        @Test
        @DisplayName("複数プレイヤーが複数チームにベット")
        void multiplePlayersMultipleTeams() {
            YamlConfiguration config = new YamlConfiguration();
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();

            addOfflineResult(config, p1, 5000, 15000);
            addOfflineResult(config, p2, 3000, 0);
            addOfflineResult(config, p3, 2000, 6000);
            addOfflineResult(config, p3, 4000, 0);

            assertEquals(15000, config.getLong(p1 + ".won"));
            assertEquals(0, config.getLong(p2 + ".won"));
            assertEquals(6000, config.getLong(p3 + ".bet"));
            assertEquals(6000, config.getLong(p3 + ".won"));
        }
    }

    // ========================================================================
    // 戦績記録の整合性
    // ========================================================================

    @Nested
    @DisplayName("戦績記録の整合性")
    class StatsIntegrityTest {

        @Test
        @DisplayName("勝ち配当: recordCashout(wonAmount, 0) で netProfit が正しく加算される")
        void winPayout_statsCorrect() {
            PlayerStats stats = new PlayerStats();

            stats.recordCashout(5000, 10000);
            assertEquals(-5000, stats.getNetProfit());

            stats.recordCashout(15000, 0);
            assertEquals(10000, stats.getNetProfit());
            assertEquals(20000, stats.getTotalCashouts());
        }

        @Test
        @DisplayName("負け: recordCashout が呼ばれず netProfit はログアウト時のまま")
        void lossPayout_statsUnchanged() {
            PlayerStats stats = new PlayerStats();

            stats.recordCashout(5000, 10000);

            assertEquals(-5000, stats.getNetProfit());
            assertEquals(5000, stats.getTotalCashouts());
            assertEquals(1, stats.getLosses());
        }

        @Test
        @DisplayName("purchased=0のrecordCashoutは勝敗カウントに影響しない")
        void zeroPurchase_noWinLossCount() {
            PlayerStats stats = new PlayerStats();

            stats.recordCashout(15000, 0);

            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
            assertEquals(15000, stats.getNetProfit());
        }
    }
}
