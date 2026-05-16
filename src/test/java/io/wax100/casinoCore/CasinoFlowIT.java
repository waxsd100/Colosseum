package io.wax100.casinoCore;

import io.wax100.casinoCore.command.ChipCommand;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.casinoCore.manager.ChipManager;
import io.wax100.casinoCore.manager.ChipManager.Chip;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.*;
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

        casinoManager = new CasinoManager(plugin);
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

    @Test
    @DisplayName("/chip 100 5 → 500E が引き落とされる")
    void 額面指定購入で正しい金額が引き落とされる() {
        chipCommand.onCommand(player, command, "chip", new String[]{"100", "5"});

        verify(economy).withdrawPlayer(player, 500.0);
    }

    @Test
    @DisplayName("/chip 100 5 → createChipItem(CHIP_100, 5) が呼ばれる")
    void 額面指定購入でチップが生成される() {
        chipCommand.onCommand(player, command, "chip", new String[]{"100", "5"});

        verify(chipManager).createChipItem(Chip.CHIP_100, 5);
    }

    // ── チップ購入（自動分割） ──

    @Test
    @DisplayName("/chip 12345 → 正しい合計金額が引き落とされる")
    void 自動分割で正しい金額が引き落とされる() {
        chipCommand.onCommand(player, command, "chip", new String[]{"12345"});

        verify(economy).withdrawPlayer(player, 12345.0);
    }

    @Test
    @DisplayName("/chip 12345 → 5種類のチップが生成される")
    void 自動分割で複数チップが生成される() {
        chipCommand.onCommand(player, command, "chip", new String[]{"12345"});

        // 10000x1, 1000x2, 100x3, 10x4, 5x1
        verify(chipManager).createChipItem(Chip.CHIP_10000, 1);
        verify(chipManager).createChipItem(Chip.CHIP_1000, 2);
        verify(chipManager).createChipItem(Chip.CHIP_100, 3);
        verify(chipManager).createChipItem(Chip.CHIP_10, 4);
        verify(chipManager).createChipItem(Chip.CHIP_5, 1);
    }

    // ── カジノOFF時 ──

    @Test
    @DisplayName("カジノOFF時にチップ購入不可")
    void カジノOFF時に購入できない() {
        casinoManager.setCasinoActive(false);

        chipCommand.onCommand(player, command, "chip", new String[]{"100", "1"});

        verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
    }

    // ── 所持金不足 ──

    @Test
    @DisplayName("所持金不足で購入失敗")
    void 所持金不足() {
        when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenReturn(false);

        chipCommand.onCommand(player, command, "chip", new String[]{"100000", "1"});

        verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
    }

    // ── 無効な額面 ──

    @Test
    @DisplayName("存在しない額面でエラー")
    void 無効な額面() {
        chipCommand.onCommand(player, command, "chip", new String[]{"999", "1"});

        verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
    }

    // ── 購入記録 ──

    @Test
    @DisplayName("購入額が CasinoManager に記録される")
    void 購入記録() {
        chipCommand.onCommand(player, command, "chip", new String[]{"5000", "2"});

        assertEquals(10000, casinoManager.getSessionPurchases(playerId));
    }

    // ── 最大購入額超過 ──

    @Test
    @DisplayName("最大購入額を超えると購入不可")
    void 最大購入額超過() {
        chipCommand.onCommand(player, command, "chip", new String[]{"2000000"});

        verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
    }
}
