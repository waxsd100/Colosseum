package io.wax100.casinoCore.manager;

import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.CasinoCore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CasinoManager の単体テスト
 * カジノ状態管理・セッション記録・ランキング・BindingCurseManager 統合を検証する
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CasinoManagerTest {

    private final UUID playerId = UUID.randomUUID();
    @Mock
    private CasinoCore plugin;
    @Mock
    private Economy economy;
    @Mock
    private BindingCurseManager bindingCurseManager;
    private CasinoManager casinoManager;

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("CasinoCore");
        when(plugin.getDataFolder()).thenReturn(
                new File(System.getProperty("java.io.tmpdir"), "CasinoCore_test_" + UUID.randomUUID()));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CasinoCore"));
        when(plugin.getEconomy()).thenReturn(economy);
        when(plugin.getChipManager()).thenReturn(mock(io.wax100.chipLib.ChipManager.class));

        casinoManager = spy(new CasinoManager(plugin, bindingCurseManager));
        // サーバー不在時 ItemStack/NBT操作が動作しないためシザース配布をスキップ
        doNothing().when(casinoManager).applyAdventureModeToPlayer(any());
    }

    // ── カジノ状態管理 ──

    @Nested
    @DisplayName("カジノ状態管理")
    class CasinoStateTest {

        @Mock
        private Player mockPlayer;

        @Mock
        private Player mockPlayer2;

        private final UUID playerId2 = UUID.randomUUID();

        @BeforeEach
        void setUpPlayer() {
            when(mockPlayer.getUniqueId()).thenReturn(playerId);
            setUpPlayerMock(mockPlayer, "world");

            when(mockPlayer2.getUniqueId()).thenReturn(playerId2);
            setUpPlayerMock(mockPlayer2, "world");
        }

        private void setUpPlayerMock(Player p, String worldName) {
            org.bukkit.World mockWorld = mock(org.bukkit.World.class);
            when(mockWorld.getName()).thenReturn(worldName);
            when(mockWorld.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)).thenReturn(false);
            when(p.getWorld()).thenReturn(mockWorld);
            org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(mockInventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);
            when(p.getInventory()).thenReturn(mockInventory);
        }

        @Test
        void 初期状態はOFF() {
            assertFalse(casinoManager.isCasinoActive());
        }

        @Test
        void 初期状態でプレイヤーは非参加() {
            assertFalse(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        void プレイヤー追加でONになる() {
            casinoManager.addPlayerToCasino(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
            assertTrue(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        void 追加されていないプレイヤーは非参加() {
            casinoManager.addPlayerToCasino(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        void 全体終了でOFFに戻る() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.setCasinoActive(false);
            assertFalse(casinoManager.isCasinoActive());
            assertFalse(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        void 連続で追加しても状態は変わらない() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
        }

        @Test
        void 複数プレイヤーを追加できる() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            assertTrue(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        void 一人退出しても他のプレイヤーは残る() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.removePlayerFromCasino(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
            assertTrue(casinoManager.isCasinoActive());
        }

        @Test
        void 一人退出後にsetCasinoActiveで全体終了できる() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.removePlayerFromCasino(mockPlayer);
            // 残り1人の状態で全体シャットダウン
            casinoManager.setCasinoActive(false);
            assertFalse(casinoManager.isCasinoActive());
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertFalse(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        void clearAllSessionDataでカジノプレイヤーもクリアされる() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.clearAllSessionData();
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertFalse(casinoManager.isCasinoActive());
            assertEquals(0, casinoManager.getSessionPurchases(playerId));
        }
    }

    // ── プレイヤー切断処理 ──

    @Nested
    @DisplayName("プレイヤー切断処理")
    class PlayerDisconnectTest {

        @Mock
        private Player mockPlayer;

        @Mock
        private Player mockPlayer2;

        private final UUID playerId2 = UUID.randomUUID();

        @BeforeEach
        void setUpPlayer() {
            when(mockPlayer.getUniqueId()).thenReturn(playerId);
            setUpPlayerMock(mockPlayer);

            when(mockPlayer2.getUniqueId()).thenReturn(playerId2);
            setUpPlayerMock(mockPlayer2);
        }

        private void setUpPlayerMock(Player p) {
            org.bukkit.World mockWorld = mock(org.bukkit.World.class);
            when(mockWorld.getName()).thenReturn("world");
            when(mockWorld.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)).thenReturn(false);
            when(p.getWorld()).thenReturn(mockWorld);
            org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(mockInventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);
            when(p.getInventory()).thenReturn(mockInventory);
        }

        @Test
        void 切断でカジノから除外される() {
            // 2人追加して1人切断 — Bukkit.getWorld 静的呼び出しを回避
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        void 切断しても購入記録は残る() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertEquals(5000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        void 他のプレイヤーが残っていればカジノONのまま() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        void 非参加プレイヤーの切断は何も起きない() {
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertFalse(casinoManager.isCasinoActive());
        }
    }

    // ── 購入記録 ──

    @Nested
    @DisplayName("購入記録")
    class SessionPurchaseTest {
        @Test
        void 購入記録が蓄積される() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.recordPurchase(playerId, 3000);
            assertEquals(8000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        void 未購入プレイヤーは0() {
            assertEquals(0, casinoManager.getSessionPurchases(UUID.randomUUID()));
        }

        @Test
        void クリアで全記録消去() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.clearAllSessionData();
            assertEquals(0, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        void 複数プレイヤーの記録は独立している() {
            UUID player2 = UUID.randomUUID();
            casinoManager.recordPurchase(playerId, 10000);
            casinoManager.recordPurchase(player2, 20000);

            assertEquals(10000, casinoManager.getSessionPurchases(playerId));
            assertEquals(20000, casinoManager.getSessionPurchases(player2));
        }

        @Test
        void クリア後も再記録できる() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.clearAllSessionData();
            casinoManager.recordPurchase(playerId, 3000);
            assertEquals(3000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        void 大量の購入記録が正しく累計される() {
            for (int i = 0; i < 100; i++) {
                casinoManager.recordPurchase(playerId, 100);
            }
            assertEquals(10000, casinoManager.getSessionPurchases(playerId));
        }
    }

    // ── ランキング ──

    @Nested
    @DisplayName("ランキング")
    class RankingTest {
        @Test
        void 損益が累計される() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            casinoManager.updateRanking(p1, 5000);
            casinoManager.updateRanking(p2, -3000);
            casinoManager.updateRanking(p1, 2000);

            var ranking = casinoManager.getSortedRanking(10);
            assertEquals(2, ranking.size());
            assertEquals(p1, ranking.get(0).getKey());
            assertEquals(7000, ranking.get(0).getValue());
            assertEquals(p2, ranking.get(1).getKey());
            assertEquals(-3000, ranking.get(1).getValue());
        }

        @Test
        void ランキングはlimit件まで() {
            for (int i = 0; i < 20; i++) {
                casinoManager.updateRanking(UUID.randomUUID(), i * 100);
            }
            assertEquals(5, casinoManager.getSortedRanking(5).size());
        }

        @Test
        void ランキングは降順() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();
            casinoManager.updateRanking(p1, 1000);
            casinoManager.updateRanking(p2, 5000);
            casinoManager.updateRanking(p3, 3000);

            List<Map.Entry<UUID, Long>> ranking = casinoManager.getSortedRanking(10);
            assertEquals(p2, ranking.get(0).getKey());
            assertEquals(p3, ranking.get(1).getKey());
            assertEquals(p1, ranking.get(2).getKey());
        }

        @Test
        void 負の損益もランキングに含まれる() {
            UUID p1 = UUID.randomUUID();
            casinoManager.updateRanking(p1, -5000);

            var ranking = casinoManager.getSortedRanking(10);
            assertEquals(1, ranking.size());
            assertEquals(-5000, ranking.get(0).getValue());
        }

        @Test
        void limit_0は空リスト() {
            casinoManager.updateRanking(UUID.randomUUID(), 1000);
            assertEquals(0, casinoManager.getSortedRanking(0).size());
        }

        @Test
        void ランキングが空の場合は空リスト() {
            assertEquals(0, casinoManager.getSortedRanking(10).size());
        }
    }

    // ── BindingCurseManager 統合 ──

    @Nested
    @DisplayName("BindingCurseManager 統合")
    class BindingCurseIntegrationTest {
        @Test
        void getBindingCurseManagerが正しいインスタンスを返す() {
            assertSame(bindingCurseManager, casinoManager.getBindingCurseManager());
        }

        @Test
        void getBindingCurseManagerがnullでない() {
            assertNotNull(casinoManager.getBindingCurseManager());
        }
    }
}
