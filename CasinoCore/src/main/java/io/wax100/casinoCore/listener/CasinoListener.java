package io.wax100.casinoCore.listener;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import io.wax100.casinoCore.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

/**
 * カジノ関連のイベントリスナー
 *
 * <p>カジノモード中の処理:
 * <ul>
 *   <li>ログイン通知（カジノモード稼働中であることを案内）</li>
 *   <li>カーペット破壊時にメタデータ付きチップをドロップ</li>
 * </ul>
 *
 * <p>チップの設置・破壊はバニラの CanPlaceOn / CanDestroy で動作する。
 */
public class CasinoListener implements Listener {

    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin CasinoCore プラグインインスタンス
     */
    public CasinoListener(CasinoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーログイン時にカジノモードの状態を通知する。
     *
     * <p>カジノモードが ON の場合、案内メッセージを送信する。
     * カジノモードが OFF の場合は何もしない。
     * ログイン時にカジノモードへの自動参加は行わない。
     * 管理者が {@code /casino on <player>} で明示的に追加する必要がある。
     *
     * @param event プレイヤー参加イベント
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getCasinoManager().isCasinoActive()) return;

        Player player = event.getPlayer();
        player.sendMessage("");
        player.sendMessage(Messages.JOIN_CASINO_ON);
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "/chip <額面> <枚数> でチップを購入できます。");
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "/chip info でチップ一覧を確認できます。");
        player.sendMessage("");
    }

    /**
     * チップカーペットを破壊した場合、メタデータ付きチップをドロップする。
     *
     * <p>CanDestroy 設定によりアドベンチャーモードでも {@link BlockBreakEvent} が発火する。
     * バニラのカーペットドロップを抑制し、代わりに額面情報付きのチップアイテムをドロップする。
     * カジノモードが OFF の場合やチップ用マテリアルでない場合は何もしない。
     *
     * @param event ブロック破壊イベント
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();

        // 軽量なマテリアルチェックを先に行い、大多数のイベントを早期リターンする
        if (!ChipManager.isChipMaterial(material)) return;
        if (!plugin.getCasinoManager().isPlayerInCasino(event.getPlayer().getUniqueId())) return;

        // 改善: O(n) ループの findChipByMaterial を Chip.fromMaterial() (O(1)) に置き換え
        Chip chip = Chip.fromMaterial(material).orElse(null);
        if (chip == null) return;

        event.setDropItems(false);
        ItemStack chipItem = plugin.getChipManager().createChipItem(chip, 1);
        block.getWorld().dropItemNaturally(block.getLocation(), chipItem);
    }

    /**
     * カジノ参加中のプレイヤーがログアウトした際の後処理を行う。
     *
     * <p>手持ちチップの金額を戦績に記録した後、ゲームモードの復元、
     * カジノシザースの回収、{@code casinoPlayers} からの除外を行う。
     * チップの換金（Vault への入金）は ChipLib の {@code onPlayerQuit}（NORMAL 優先度）で行われる。
     * 既にベットしている場合、ベットは有効のまま保持され、
     * 次回ログイン時に {@link io.wax100.casinoCore.manager.OfflinePayoutManager} が結果を清算する。
     *
     * <p>このハンドラは {@link EventPriority#LOWEST} で登録されているため、
     * ChipLib がチップを消去する前に {@code calculateTotalValue} を実行できる。
     *
     * @param event プレイヤー退出イベント
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCasinoManager().isPlayerInCasino(player.getUniqueId())) return;

        plugin.getCasinoManager().handlePlayerDisconnect(player);
    }

    /**
     * カジノ参加中のプレイヤーがキックされた際の後処理。
     *
     * <p>PlayerKickEvent は一部の実装で PlayerQuitEvent が発火しない
     * ケースがあるため、同一の切断処理を明示的に呼び出す。
     *
     * @param event プレイヤーキックイベント
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCasinoManager().isPlayerInCasino(player.getUniqueId())) return;

        plugin.getCasinoManager().handlePlayerDisconnect(player);
    }
}
