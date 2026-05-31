package io.wax100.chipLib.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link RankingManager} の単体テスト。
 *
 * <p>カテゴリ別ランキング・総合ランキング・データ永続化を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RankingManager: ランキング管理")
class RankingManagerTest {

    @TempDir
    File tempDir;

    @Mock
    private JavaPlugin plugin;

    private RankingManager rankingManager;

    @BeforeEach
    void setUp() {
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RankingManagerTest"));
        when(plugin.isEnabled()).thenReturn(false); // 同期保存を強制
        rankingManager = new RankingManager(plugin);
    }

    // ── カテゴリ別ランキング ──

    @Nested
    @DisplayName("カテゴリ別ランキング")
    class CategoryRankingTest {
        @Test
        @DisplayName("損益が累計される")
        void profitAndLossAccumulate() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 5000);
            rankingManager.updateRanking("casino", p2, -3000);
            rankingManager.updateRanking("casino", p1, 2000);

            var ranking = rankingManager.getSortedRanking("casino", 10);
            assertEquals(2, ranking.size());
            assertEquals(p1, ranking.get(0).getKey());
            assertEquals(7000, ranking.get(0).getValue());
            assertEquals(p2, ranking.get(1).getKey());
            assertEquals(-3000, ranking.get(1).getValue());
        }

        @Test
        @DisplayName("ランキングはlimit件まで")
        void rankingIsLimitedByLimit() {
            for (int i = 0; i < 20; i++) {
                rankingManager.updateRanking("casino", UUID.randomUUID(), i * 100);
            }
            assertEquals(5, rankingManager.getSortedRanking("casino", 5).size());
        }

        @Test
        @DisplayName("ランキングは降順")
        void rankingIsInDescendingOrder() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 1000);
            rankingManager.updateRanking("casino", p2, 5000);
            rankingManager.updateRanking("casino", p3, 3000);

            List<Map.Entry<UUID, Long>> ranking = rankingManager.getSortedRanking("casino", 10);
            assertEquals(p2, ranking.get(0).getKey());
            assertEquals(p3, ranking.get(1).getKey());
            assertEquals(p1, ranking.get(2).getKey());
        }

        @Test
        @DisplayName("負の損益もランキングに含まれる")
        void negativeProfitIsIncludedInRanking() {
            UUID p1 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, -5000);

            var ranking = rankingManager.getSortedRanking("casino", 10);
            assertEquals(1, ranking.size());
            assertEquals(-5000, ranking.get(0).getValue());
        }

        @Test
        @DisplayName("limit=0は空リスト")
        void limitZeroReturnsEmptyList() {
            rankingManager.updateRanking("casino", UUID.randomUUID(), 1000);
            assertEquals(0, rankingManager.getSortedRanking("casino", 0).size());
        }

        @Test
        @DisplayName("ランキングが空の場合は空リスト")
        void emptyRankingReturnsEmptyList() {
            assertEquals(0, rankingManager.getSortedRanking("casino", 10).size());
        }

        @Test
        @DisplayName("存在しないカテゴリは空リスト")
        void nonExistentCategoryReturnsEmpty() {
            assertEquals(0, rankingManager.getSortedRanking("nonexistent", 10).size());
        }
    }

    // ── 総合ランキング ──

    @Nested
    @DisplayName("総合ランキング")
    class TotalRankingTest {
        @Test
        @DisplayName("全カテゴリの合算が正しい")
        void totalRankingCombinesAllCategories() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 5000);
            rankingManager.updateRanking("arena", p1, 3000);
            rankingManager.updateRanking("casino", p2, -2000);
            rankingManager.updateRanking("arena", p2, 10000);

            var total = rankingManager.getTotalRanking(10);
            assertEquals(2, total.size());
            // p1: 5000 + 3000 = 8000, p2: -2000 + 10000 = 8000
            // 同スコアの場合、順序は安定していればよい
            long p1Total = total.stream()
                    .filter(e -> e.getKey().equals(p1))
                    .findFirst().get().getValue();
            long p2Total = total.stream()
                    .filter(e -> e.getKey().equals(p2))
                    .findFirst().get().getValue();
            assertEquals(8000, p1Total);
            assertEquals(8000, p2Total);
        }

        @Test
        @DisplayName("単一カテゴリのみの総合ランキング")
        void totalRankingWithSingleCategory() {
            UUID p1 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 5000);

            var total = rankingManager.getTotalRanking(10);
            assertEquals(1, total.size());
            assertEquals(5000, total.get(0).getValue());
        }

        @Test
        @DisplayName("空の場合は空リスト")
        void emptyTotalRanking() {
            assertEquals(0, rankingManager.getTotalRanking(10).size());
        }
    }

    // ── カテゴリ別リセット ──

    @Nested
    @DisplayName("リセット")
    class ResetTest {
        @Test
        @DisplayName("カテゴリ別リセット")
        void resetCategory() {
            UUID p1 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 5000);
            rankingManager.updateRanking("arena", p1, 3000);

            rankingManager.resetRanking("casino");
            assertEquals(0, rankingManager.getSortedRanking("casino", 10).size());
            assertEquals(1, rankingManager.getSortedRanking("arena", 10).size());
        }

        @Test
        @DisplayName("全カテゴリリセット")
        void resetAll() {
            rankingManager.updateRanking("casino", UUID.randomUUID(), 5000);
            rankingManager.updateRanking("arena", UUID.randomUUID(), 3000);

            rankingManager.resetAllRankings();
            assertEquals(0, rankingManager.getSortedRanking("casino", 10).size());
            assertEquals(0, rankingManager.getSortedRanking("arena", 10).size());
            assertEquals(0, rankingManager.getTotalRanking(10).size());
        }
    }

    // ── データ永続化 ──

    @Nested
    @DisplayName("データ永続化")
    class PersistenceTest {
        @Test
        @DisplayName("保存・再読み込みでデータが復元される")
        void saveAndLoadRoundTrip() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            rankingManager.updateRanking("casino", p1, 5000);
            rankingManager.updateRanking("arena", p2, -3000);
            rankingManager.saveData(); // ダーティフラグ方式のため明示的に保存

            // 新しい RankingManager で再読み込み
            RankingManager newManager = new RankingManager(plugin);
            var casinoRanking = newManager.getSortedRanking("casino", 10);
            var arenaRanking = newManager.getSortedRanking("arena", 10);

            assertEquals(1, casinoRanking.size());
            assertEquals(p1, casinoRanking.get(0).getKey());
            assertEquals(5000, casinoRanking.get(0).getValue());

            assertEquals(1, arenaRanking.size());
            assertEquals(p2, arenaRanking.get(0).getKey());
            assertEquals(-3000, arenaRanking.get(0).getValue());
        }

        @Test
        @DisplayName("ranking_data.yml が作成される")
        void dataFileIsCreated() {
            rankingManager.updateRanking("casino", UUID.randomUUID(), 1000);
            rankingManager.saveData(); // ダーティフラグ方式のため明示的に保存

            File dataFile = new File(tempDir, "ranking_data.yml");
            assertTrue(dataFile.exists());
        }
    }
}
