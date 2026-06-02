package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.BettingManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.ChipPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * カーペット設置・破壊によるベットイベントリスナー。
 *
 * <p>ベット受付中に、チームのベットエリア内にカーペット（チップ）を設置すると
 * 自動的にベットとして記録される。回収するとベット取消。
 */
public class ArenaBettingListener implements Listener {

    private final ArenaCore plugin;

    public ArenaBettingListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * カーペット設置時の処理。
     *
     * <p>条件:
     * <ol>
     *   <li>闘技場セッションが BETTING 状態</li>
     *   <li>設置アイテムがカジノチップ</li>
     *   <li>設置座標がチームのベットエリア内</li>
     *   <li>プレイヤーが戦闘員でない</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ArenaManager arenaManager = plugin.getArenaManager();
        if (!arenaManager.hasActiveSession()) return;

        ArenaSession session = arenaManager.getActiveSession();
        if (session.getState() != ArenaState.BETTING && session.getState() != ArenaState.BLIND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // チップかどうか判定
        ChipManager chipManager = plugin.getChipManager();
        if (chipManager == null || !chipManager.isChip(item)) return;

        // 戦闘員チェック
        if (session.isFighter(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "戦闘員はベットに参加できません。");
            return;
        }

        // ベットエリアの判定
        RegionManager regionManager = plugin.getRegionManager();
        String teamName = regionManager.getTeamForLocation(event.getBlock().getLocation());

        if (teamName == null) {
            // ベットエリア外 → 設置をキャンセルしてプレイヤーに通知
            event.setCancelled(true);
            if (regionManager.hasAnyRegion()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.RED + "ベットエリア外です！"));
            } else {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "ベットエリアが設定されていません。" + ChatColor.GRAY + " → /arena region <チーム名>");
            }
            return;
        }

        // 同座標にチップが既に設置されている場合はキャンセル
        ArenaSession.PlacedChipInfo existingChip = session.getPlacedChip(event.getBlock().getLocation());
        if (existingChip != null) {
            event.setCancelled(true);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.RED + "この場所には既にチップがあります"));
            return;
        }

        long chipValue = chipManager.getChipValue(item);

        // ベット処理
        BettingManager bettingManager = plugin.getBettingManager();
        boolean success = bettingManager.placeBet(session, player, teamName, chipValue,
                event.getBlock().getLocation(), event.getBlockReplacedState().getType());
        // ベット記録に失敗した場合はカーペット設置をキャンセル
        if (!success) {
            event.setCancelled(true);
        }
    }

    /**
     * カーペット破壊時の処理（ベット取消）。
     *
     * <p>ベット受付中のみ、自分が置いたカーペットを回収可能。
     * 試合開始後は回収不可。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaManager arenaManager = plugin.getArenaManager();
        if (!arenaManager.hasActiveSession()) return;

        ArenaSession session = arenaManager.getActiveSession();
        Block block = event.getBlock();

        // この座標にベットチップがあるか
        ArenaSession.PlacedChipInfo chipInfo = session.getPlacedChip(block.getLocation());
        if (chipInfo == null) return;

        Player player = event.getPlayer();

        // ベット受付中（BETTING/BLIND）: 自分のチップのみ回収可能
        if (session.getState() == ArenaState.BETTING || session.getState() == ArenaState.BLIND) {
            if (!chipInfo.playerId().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "他人のベットカーペットは回収できません。");
                return;
            }

            BettingManager bettingManager = plugin.getBettingManager();
            bettingManager.cancelBet(session, player, block.getLocation());
            // カーペットのドロップを防止（ブロック除去はcancelBet側のMaterial.AIR設定で実施）
            event.setCancelled(true);
            return;
        }

        // BETTING/BLIND 以外の状態（CLOSED, ACTIVE, SETUP, FINISHED等）では回収不可
        event.setCancelled(true);
        if (session.getState() == ArenaState.ACTIVE) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "試合中はベットカーペットを回収できません。");
        } else {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "現在、ベットカーペットの操作はできません。");
        }
    }

    /**
     * 途中参加プレイヤーへのチップ使用許可。
     *
     * <p>ベット受付中にサーバーへ参加したプレイヤーに対して、
     * チップ購入コマンドの使用を自動的に許可する。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ArenaManager arenaManager = plugin.getArenaManager();
        if (!arenaManager.hasActiveSession()) return;

        ArenaSession session = arenaManager.getActiveSession();
        ArenaState state = session.getState();
        if (state != ArenaState.BETTING && state != ArenaState.BLIND && state != ArenaState.CLOSED) return;

        Plugin chipLibPlugin = Bukkit.getPluginManager().getPlugin("ChipLib");
        if (chipLibPlugin instanceof ChipPlugin chipPlugin) {
            chipPlugin.allowPlayer(event.getPlayer().getUniqueId());
        }
    }
}
