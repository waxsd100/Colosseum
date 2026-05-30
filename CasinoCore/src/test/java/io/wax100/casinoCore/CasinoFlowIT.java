package io.wax100.casinoCore;

import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.command.ChipCommand;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * チップ購入フローの統合テスト
 * ChipCommand → ChipManager → Economy の連携を検証する
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CasinoFlowIT {

    private final UUID playerId = UUID.randomUUID();
    @Mock
    private CasinoCore plugin;
    @Mock
    private Economy economy;
    @Mock
    private Player player;
    @Mock
    private PlayerInventory inventory;
    @Mock
    private Command command;
    @Mock
    private BindingCurseManager bindingCurseManager;
    private ChipManager chipManager;
    private CasinoManager casinoManager;
    private ChipCommand chipCommand;

    @BeforeEach
    void setUp() {
        // Plugin モック設定
        when(plugin.getName()).thenReturn("CasinoCore");
        when(plugin.getEconomy()).thenReturn(economy);
        when(plugin.getConfig()).thenReturn(mock(org.bukkit.configuration.file.FileConfiguration.class));
        when(plugin.getConfig().getLong("max-buy", 1000000)).thenReturn(1000000L);
        when(plugin.getDataFolder()).thenReturn(new File(System.getProperty("java.io.tmpdir"), "CasinoCore_test"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CasinoCore"));

        // ChipManager をスパイ化して createChipItem のモックアイテムを返す
        chipManager = spy(new ChipManager(plugin));
        doAnswer(invocation -> {
            Chip chip = invocation.getArgument(0);
            int amount = invocation.getArgument(1);
            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.getType()).thenReturn(chip.getMaterial());
            when(mockItem.getAmount()).thenReturn(amount);
            return mockItem;
        }).when(chipManager).createChipItem(any(Chip.class), anyInt());

        casinoManager = new CasinoManager(plugin, bindingCurseManager);
        when(plugin.getChipManager()).thenReturn(chipManager);
        when(plugin.getCasinoManager()).thenReturn(casinoManager);

        chipCommand = new ChipCommand(plugin);

        // Player モック設定
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("TestPlayer");
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(inventory.getContents()).thenReturn(new ItemStack[41]);
        when(inventory.addItem(any())).thenReturn(new HashMap<>());

        // Economy デフォルト: 十分な残高あり
        when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null));
        when(economy.getBalance(any(org.bukkit.OfflinePlayer.class))).thenReturn(1000000.0);

        casinoManager.setCasinoActive(true);
    }

    // ── チップ購入（額面指定） ──

    @Nested
    @DisplayName("額面指定購入")
    class DenominationPurchaseTest {
        @Test
        @DisplayName("/chip 100 5 → 500E が引き落とされる")
        void 正しい金額が引き落とされる() {
            chipCommand.onCommand(player, command, "chip", new String[]{"100", "5"});

            verify(economy).withdrawPlayer(player, 500.0);
        }

        @Test
        @DisplayName("/chip 100 5 → createChipItem(CHIP_100, 5) が呼ばれる")
        void チップが生成される() {
            chipCommand.onCommand(player, command, "chip", new String[]{"100", "5"});

            verify(chipManager).createChipItem(Chip.CHIP_100, 5);
        }

        @Test
        @DisplayName("/chip 1000000 1 → 最高額チップの購入")
        void 最高額チップが購入できる() {
            chipCommand.onCommand(player, command, "chip", new String[]{"1000000", "1"});

            verify(economy).withdrawPlayer(player, 1000000.0);
            verify(chipManager).createChipItem(Chip.CHIP_1000000, 1);
        }

        @Test
        @DisplayName("/chip 1 64 → 最低額チップの大量購入")
        void 最低額チップを大量購入できる() {
            chipCommand.onCommand(player, command, "chip", new String[]{"1", "64"});

            verify(economy).withdrawPlayer(player, 64.0);
            verify(chipManager).createChipItem(Chip.CHIP_1, 64);
        }
    }

    // ── チップ購入（自動分割） ──

    @Nested
    @DisplayName("自動分割購入")
    class AutoSplitPurchaseTest {
        @Test
        @DisplayName("/chip 12345 → 正しい合計金額が引き落とされる")
        void 正しい金額が引き落とされる() {
            chipCommand.onCommand(player, command, "chip", new String[]{"12345"});

            verify(economy).withdrawPlayer(player, 12345.0);
        }

        @Test
        @DisplayName("/chip 12345 → 5種類のチップが生成される")
        void 複数チップが生成される() {
            chipCommand.onCommand(player, command, "chip", new String[]{"12345"});

            // 10000x1, 1000x2, 100x3, 10x4, 5x1
            verify(chipManager).createChipItem(Chip.CHIP_10000, 1);
            verify(chipManager).createChipItem(Chip.CHIP_1000, 2);
            verify(chipManager).createChipItem(Chip.CHIP_100, 3);
            verify(chipManager).createChipItem(Chip.CHIP_10, 4);
            verify(chipManager).createChipItem(Chip.CHIP_5, 1);
        }

        @Test
        @DisplayName("/chip 1000000 → 最高額1枚のみ")
        void ぴったりの額面は1枚() {
            chipCommand.onCommand(player, command, "chip", new String[]{"1000000"});

            verify(chipManager).createChipItem(Chip.CHIP_1000000, 1);
            verify(economy).withdrawPlayer(player, 1000000.0);
        }

        @Test
        @DisplayName("/chip 1 → 最小額チップ1枚")
        void 最小額は1E_チップ() {
            chipCommand.onCommand(player, command, "chip", new String[]{"1"});

            verify(chipManager).createChipItem(Chip.CHIP_1, 1);
        }
    }

    // ── バリデーション ──

    @Nested
    @DisplayName("バリデーション")
    class ValidationTest {
        @Test
        @DisplayName("カジノOFF時にチップ購入不可")
        void カジノOFF時に購入できない() {
            casinoManager.setCasinoActive(false);

            chipCommand.onCommand(player, command, "chip", new String[]{"100", "1"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("所持金不足で購入失敗")
        void 所持金不足() {
            when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenReturn(false);

            chipCommand.onCommand(player, command, "chip", new String[]{"100000", "1"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("存在しない額面でエラー")
        void 無効な額面() {
            chipCommand.onCommand(player, command, "chip", new String[]{"999", "1"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("最大購入額を超えると購入不可")
        void 最大購入額超過() {
            chipCommand.onCommand(player, command, "chip", new String[]{"2000000"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("枚数0は購入不可")
        void 枚数0は拒否() {
            chipCommand.onCommand(player, command, "chip", new String[]{"100", "0"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("負の枚数は購入不可")
        void 負の枚数は拒否() {
            chipCommand.onCommand(player, command, "chip", new String[]{"100", "-1"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("金額0は自動分割で購入不可")
        void 金額0は拒否() {
            chipCommand.onCommand(player, command, "chip", new String[]{"0"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("負の金額は自動分割で購入不可")
        void 負の金額は拒否() {
            chipCommand.onCommand(player, command, "chip", new String[]{"-100"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("インベントリ満杯で購入不可")
        void インベントリ満杯() {
            // 全スロットが埋まっている状態
            ItemStack[] fullContents = new ItemStack[36];
            for (int i = 0; i < fullContents.length; i++) {
                fullContents[i] = mock(ItemStack.class);
                when(fullContents[i].getType()).thenReturn(org.bukkit.Material.STONE);
            }
            when(inventory.getStorageContents()).thenReturn(fullContents);

            chipCommand.onCommand(player, command, "chip", new String[]{"100", "1"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }
    }

    // ── 購入記録 ──

    @Nested
    @DisplayName("購入記録")
    class PurchaseRecordTest {
        @Test
        @DisplayName("購入額が CasinoManager に記録される")
        void 購入記録() {
            chipCommand.onCommand(player, command, "chip", new String[]{"5000", "2"});

            assertEquals(10000, casinoManager.getSessionPurchases(playerId));
        }

        @Test
        @DisplayName("複数回の購入が累計される")
        void 複数回購入の累計() {
            chipCommand.onCommand(player, command, "chip", new String[]{"100", "5"});
            chipCommand.onCommand(player, command, "chip", new String[]{"1000", "3"});

            assertEquals(500 + 3000, casinoManager.getSessionPurchases(playerId));
        }
    }

    // ── info / balance / cashout サブコマンド ──

    @Nested
    @DisplayName("サブコマンド")
    class SubCommandTest {
        @Test
        @DisplayName("/chip info → Economyへのアクセスなし、メッセージ送信のみ")
        void infoはEconomy不使用() {
            chipCommand.onCommand(player, command, "chip", new String[]{"info"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
            verify(player, atLeastOnce()).sendMessage(anyString());
        }

        @Test
        @DisplayName("/chip balance → メッセージ送信のみ")
        void balanceはメッセージ送信() {
            chipCommand.onCommand(player, command, "chip", new String[]{"balance"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
            verify(player, atLeastOnce()).sendMessage(anyString());
        }

        @Test
        @DisplayName("/chip cashout → カジノOFF時はエラー")
        void cashoutはカジノOFF時エラー() {
            casinoManager.setCasinoActive(false);

            chipCommand.onCommand(player, command, "chip", new String[]{"cashout"});

            verify(economy, never()).depositPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("/chip → 引数なしでヘルプ表示")
        void 引数なしでヘルプ() {
            chipCommand.onCommand(player, command, "chip", new String[]{});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
            verify(player, atLeastOnce()).sendMessage(anyString());
        }

        @Test
        @DisplayName("不正な文字列引数でヘルプ表示")
        void 不正な引数でヘルプ() {
            chipCommand.onCommand(player, command, "chip", new String[]{"abc"});

            verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }
    }
}
