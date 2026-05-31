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
            when(p.isOnline()).thenReturn(true);
            org.bukkit.World mockWorld = mock(org.bukkit.World.class);
            when(mockWorld.getName()).thenReturn(worldName);
            when(mockWorld.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)).thenReturn(false);
            when(p.getWorld()).thenReturn(mockWorld);
            org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(mockInventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);
            when(p.getInventory()).thenReturn(mockInventory);
        }

        @Test
        @DisplayName("初期状態はOFF")
        void initialStateIsOff() {
            assertFalse(casinoManager.isCasinoActive());
        }

        @Test
        @DisplayName("初期状態でプレイヤーは非参加")
        void playerNotInCasinoInitially() {
            assertFalse(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        @DisplayName("プレイヤー追加でONになる")
        void addingPlayerActivatesCasino() {
            casinoManager.addPlayerToCasino(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
            assertTrue(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        @DisplayName("追加されていないプレイヤーは非参加")
        void nonAddedPlayerIsNotInCasino() {
            casinoManager.addPlayerToCasino(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        @DisplayName("全体終了でOFFに戻る")
        void deactivatingCasinoTurnsOff() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.setCasinoActive(false);
            assertFalse(casinoManager.isCasinoActive());
            assertFalse(casinoManager.isPlayerInCasino(playerId));
        }

        @Test
        @DisplayName("連続で追加しても状態は変わらない")
        void duplicateAddDoesNotChangeState() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
        }

        @Test
        @DisplayName("複数プレイヤーを追加できる")
        void canAddMultiplePlayers() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            assertTrue(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        @DisplayName("一人退出しても他のプレイヤーは残る")
        void otherPlayersRemainAfterOneLeaves() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.removePlayerFromCasino(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
            assertTrue(casinoManager.isCasinoActive());
        }

        @Test
        @DisplayName("一人退出後にsetCasinoActiveで全体終了できる")
        void canDeactivateAfterOnePlayerLeaves() {
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
        @DisplayName("clearAllSessionDataでカジノプレイヤーもクリアされる")
        void clearAllSessionDataAlsoClearsCasinoPlayers() {
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
            when(p.isOnline()).thenReturn(true);
            org.bukkit.World mockWorld = mock(org.bukkit.World.class);
            when(mockWorld.getName()).thenReturn("world");
            when(mockWorld.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)).thenReturn(false);
            when(p.getWorld()).thenReturn(mockWorld);
            org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(mockInventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);
            when(p.getInventory()).thenReturn(mockInventory);
        }

        @Test
        @DisplayName("切断でカジノから除外される")
        void disconnectRemovesPlayerFromCasino() {
            // 2人追加して1人切断 — Bukkit.getWorld 静的呼び出しを回避
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertFalse(casinoManager.isPlayerInCasino(playerId));
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        @DisplayName("切断しても購入記録は残る")
        void purchaseRecordRemainsAfterDisconnect() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertEquals(5000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        @DisplayName("他のプレイヤーが残っていればカジノONのまま")
        void casinoRemainsActiveIfOtherPlayersExist() {
            casinoManager.addPlayerToCasino(mockPlayer);
            casinoManager.addPlayerToCasino(mockPlayer2);
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertTrue(casinoManager.isCasinoActive());
            assertTrue(casinoManager.isPlayerInCasino(playerId2));
        }

        @Test
        @DisplayName("非参加プレイヤーの切断は何も起きない")
        void disconnectOfNonParticipantDoesNothing() {
            casinoManager.handlePlayerDisconnect(mockPlayer);
            assertFalse(casinoManager.isCasinoActive());
        }
    }

    // ── 購入記録 ──

    @Nested
    @DisplayName("購入記録")
    class SessionPurchaseTest {
        @Test
        @DisplayName("購入記録が蓄積される")
        void purchaseRecordsAccumulate() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.recordPurchase(playerId, 3000);
            assertEquals(8000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        @DisplayName("未購入プレイヤーは0")
        void noPurchasePlayerReturnsZero() {
            assertEquals(0, casinoManager.getSessionPurchases(UUID.randomUUID()));
        }

        @Test
        @DisplayName("クリアで全記録消去")
        void clearRemovesAllRecords() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.clearAllSessionData();
            assertEquals(0, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        @DisplayName("複数プレイヤーの記録は独立している")
        void multiplePlayerRecordsAreIndependent() {
            UUID player2 = UUID.randomUUID();
            casinoManager.recordPurchase(playerId, 10000);
            casinoManager.recordPurchase(player2, 20000);

            assertEquals(10000, casinoManager.getSessionPurchases(playerId));
            assertEquals(20000, casinoManager.getSessionPurchases(player2));
        }

        @Test
        @DisplayName("クリア後も再記録できる")
        void canRecordAgainAfterClear() {
            casinoManager.recordPurchase(playerId, 5000);
            casinoManager.clearAllSessionData();
            casinoManager.recordPurchase(playerId, 3000);
            assertEquals(3000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        @DisplayName("大量の購入記録が正しく累計される")
        void largeBulkPurchasesAccumulateCorrectly() {
            for (int i = 0; i < 100; i++) {
                casinoManager.recordPurchase(playerId, 100);
            }
            assertEquals(10000, casinoManager.getSessionPurchases(playerId));
        }
    }

    // ── ランキング（RankingManager 委譲） ──

    @Nested
    @DisplayName("ランキング（RankingManager 委譲）")
    class RankingTest {
        @Test
        @DisplayName("RankingManager 未接続時、updateRanking は例外を投げない")
        void updateRankingDoesNotThrowWithoutRankingManager() {
            assertDoesNotThrow(() -> casinoManager.updateRanking(UUID.randomUUID(), 5000));
        }

        @Test
        @DisplayName("RankingManager 未接続時、getSortedRanking は空リスト")
        void getSortedRankingReturnsEmptyWithoutRankingManager() {
            assertEquals(0, casinoManager.getSortedRanking(10).size());
        }

        @Test
        @DisplayName("resetRanking は例外を投げない")
        void resetRankingDoesNotThrow() {
            assertDoesNotThrow(() -> casinoManager.resetRanking());
        }
    }

    // ── BindingCurseManager 統合 ──

    @Nested
    @DisplayName("BindingCurseManager 統合")
    class BindingCurseIntegrationTest {
        @Test
        @DisplayName("getBindingCurseManagerが正しいインスタンスを返す")
        void getBindingCurseManagerReturnsCorrectInstance() {
            assertSame(bindingCurseManager, casinoManager.getBindingCurseManager());
        }

        @Test
        @DisplayName("getBindingCurseManagerがnullでない")
        void getBindingCurseManagerIsNotNull() {
            assertNotNull(casinoManager.getBindingCurseManager());
        }
    }
}
