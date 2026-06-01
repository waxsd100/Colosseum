package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;
import io.wax100.arenaCore.payout.PayoutStrategy;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 賭け管理マネージャ。
 *
 * <p>物理カーペット設置ベースの賭けを処理する:
 * <ul>
 *   <li>カーペット設置時の賭け記録</li>
 *   <li>カーペット回収時の賭け取消</li>
 *   <li>オッズのブロードキャスト</li>
 *   <li>配当計算と配布</li>
 *   <li>全額返金（キャンセル時）</li>
 * </ul>
 */
public class BettingManager {

    private final ArenaCore plugin;

    public BettingManager(ArenaCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    }

    /**
     * カーペットチップの設置を賭けとして処理する。
     *
     * @param session   セッション
     * @param player    設置者
     * @param teamName  賭け先チーム
     * @param chipValue チップ額面
     * @param location  設置座標
     * @return 賭けとして記録された場合 {@code true}
     */
    public boolean placeBet(ArenaSession session, Player player, String teamName,
                            long chipValue, Location location, Material originalBlock) {
        // 賭け記録
        try {
            session.addOrUpdateBet(player.getUniqueId(), teamName, chipValue);
        } catch (IllegalStateException e) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + e.getMessage());
            return false;
        }
        session.addPlacedChip(location, player.getUniqueId(), teamName, chipValue, originalBlock);

        // 固定オッズ方式の場合、オッズをロック
        String payoutMethod = plugin.getConfig().getString("payout-method", "pari-mutuel");
        if ("fixed-odds".equals(payoutMethod)) {
            double houseEdge = plugin.getHouseEdge();
            Bet bet = session.getBet(player.getUniqueId(), teamName);
            if (bet != null) {
                double currentOdds = plugin.getPayoutStrategy().calculateOdds(session, teamName, houseEdge);
                bet.setLockedOdds(currentOdds);
            }
        }

        ChatColor teamColor = session.getTeamColor(teamName);

        String msg = ChatColor.GREEN + "✔ " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に "
                + ChatColor.YELLOW + ChipManager.formatAmount(chipValue) + " E"
                + ChatColor.GREEN + " 賭け"
                + ChatColor.GRAY + " (合計: "
                + ChipManager.formatAmount(session.getBet(player.getUniqueId(), teamName).amount()) + " E)";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));

        return true;
    }

    /**
     * 設置カーペットの回収（賭け取消）を処理する。
     *
     * @param session  セッション
     * @param player   回収者
     * @param location 回収座標
     */
    public void cancelBet(ArenaSession session, Player player, Location location) {
        ArenaSession.PlacedChipInfo chipInfo = session.getPlacedChip(location);
        if (chipInfo == null) return;

        // 自分の賭けのみ取消可能
        if (!chipInfo.playerId().equals(player.getUniqueId())) return;

        session.removePlacedChip(location);

        // カーペットブロックを除去（ドロップ防止のため直接AIRに設定）
        location.getBlock().setType(org.bukkit.Material.AIR);

        // 賭け金額の更新（減算）
        Bet bet = session.getBet(player.getUniqueId(), chipInfo.teamName());
        if (bet != null) {
            if (bet.amount() >= chipInfo.chipValue()) {
                bet.addAmount(-chipInfo.chipValue());
                // 金額が0以下になった場合は賭け自体を削除
                if (bet.amount() <= 0) {
                    session.removeBet(player.getUniqueId(), chipInfo.teamName());
                }
            } else {
                // データ不整合 — 賭け自体を削除
                session.removeBet(player.getUniqueId(), chipInfo.teamName());
            }
        }

        // 視覚エフェクト: 回収演出
        playChipBreakEffect(location);

        String msg = ChatColor.YELLOW + "↩ "
                + ChipManager.formatAmount(chipInfo.chipValue())
                + " E 取消";
        Bet remainingBet = session.getBet(player.getUniqueId(), chipInfo.teamName());
        if (remainingBet != null && remainingBet.amount() > 0) {
            msg += ChatColor.GRAY + " (残: " + ChipManager.formatAmount(remainingBet.amount()) + " E)";
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    /**
     * オッズをブロードキャストする。
     *
     * @param session セッション
     */
    public void broadcastOdds(ArenaSession session) {
        double houseEdge = plugin.getHouseEdge();
        PayoutStrategy strategy = plugin.getPayoutStrategy();
        long totalPool = session.getTotalPool();

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "📊 現在のオッズ:");
        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = session.getTeamColor(team);
            long teamPool = session.getTeamPool(team);
            double odds = strategy.calculateOdds(session, team, houseEdge);

            String oddsStr = teamPool > 0 ? String.format("%.2f倍", odds) : "---";
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " | " + ChatColor.WHITE + oddsStr
                    + ChatColor.GRAY + " | プール: "
                    + ChatColor.YELLOW + ChipManager.formatAmount(teamPool) + " E");
        }
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "  総プール: " + ChatColor.YELLOW + ChipManager.formatAmount(totalPool) + " E");
    }

    /**
     * 配当を計算し配布する。
     *
     * @param session     セッション
     * @param winningTeam 勝利チーム名
     */
    public void calculateAndDistributePayout(ArenaSession session, String winningTeam) {
        double houseEdge = plugin.getHouseEdge();
        PayoutStrategy strategy = plugin.getPayoutStrategy();
        Map<UUID, Long> payouts = strategy.calculatePayouts(session, winningTeam, houseEdge);

        long totalPool = session.getTotalPool();
        long totalPayout = 0;

        // 設置されたカーペットを全削除
        clearPlacedChips(session);

        // 配当配布
        for (Map.Entry<UUID, Long> entry : payouts.entrySet()) {
            UUID playerId = entry.getKey();
            long payout = entry.getValue();
            totalPayout += payout;

            distributeAmount(playerId, payout);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Bet playerBet = session.getBet(playerId, winningTeam);
                if (playerBet == null) continue;
                long originalBet = playerBet.amount();
                long profit = payout - originalBet;

                player.sendMessage("");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "🎉 配当受取！");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "賭け金: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(originalBet) + " E");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "配当: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(payout) + " E");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "損益: "
                        + (profit >= 0 ? ChatColor.GREEN + "+"
                        : ChatColor.RED.toString())
                        + ChipManager.formatAmount(profit) + " E");
                player.sendMessage("");
            } else {
                plugin.getLogger().warning("オフラインプレイヤーへの配当をVault経由で入金: " + playerId + " / " + payout + " E");
            }
        }

        // 負けた人への通知
        for (Bet bet : session.getAllBets()) {
            if (!bet.teamName().equals(winningTeam)) {
                Player player = Bukkit.getPlayer(bet.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "残念… 賭け金 " + ChatColor.YELLOW
                            + ChipManager.formatAmount(bet.amount()) + " E"
                            + ChatColor.RED + " は没収されました。");
                    player.sendMessage("");
                }
            }
        }

        // 運営収益
        long houseProfit = totalPool - totalPayout + session.getEntryFeePool();
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "運営収益: " + ChatColor.YELLOW + ChipManager.formatAmount(houseProfit) + " E");
    }

    /**
     * 全賭け金を返金する（キャンセル時）。
     *
     * @param session セッション
     */
    public void refundAll(ArenaSession session) {
        // 設置カーペットを全削除
        clearPlacedChips(session);

        // 賭け者に返金（チップとして）
        for (Bet bet : session.getAllBets()) {
            if (bet.amount() <= 0) continue;

            distributeAmount(bet.playerId(), bet.amount());

            Player player = Bukkit.getPlayer(bet.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "賭け金 " + ChatColor.WHITE
                        + ChipManager.formatAmount(bet.amount()) + " E"
                        + ChatColor.YELLOW + " をチップで返金しました。");
            } else {
                plugin.getLogger().warning("オフラインプレイヤーへの返金をVault経由で実行: "
                        + bet.playerId() + " / " + bet.amount() + " E");
            }
        }
    }

    /**
     * チップまたはVault経由で指定額をプレイヤーに配布する。
     *
     * <p>オンラインプレイヤーにはチップとして配布し、インベントリが不足する場合は
     * 足元にドロップする。オフラインプレイヤーにはVault経由で入金する。
     *
     * @param playerId 配布先プレイヤーのUUID
     * @param amount   配布額
     */
    private void distributeAmount(UUID playerId, long amount) {
        ChipManager chipManager = plugin.getChipManager();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<Chip, Integer> chips = chipManager.breakdownAmount(amount);
            int slotsNeeded = chipManager.calculateSlotsNeeded(chips);
            int emptySlots = chipManager.countEmptySlots(player);
            if (emptySlots < slotsNeeded) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "⚠️ インベントリがいっぱいのため、一部のチップが足元にドロップされます。");
            }
            chipManager.giveChips(player, chips);
        } else {
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(offlinePlayer, amount);
            } else {
                plugin.getLogger().severe("Vault経済APIが利用不可: プレイヤー " + playerId + " への " + amount + " E が喪失しました");
            }
        }
    }

    /**
     * 設置されたカーペットチップを全てワールドから削除する。
     *
     * @param session セッション
     */
    private void clearPlacedChips(ArenaSession session) {
        for (Map.Entry<Location, ArenaSession.PlacedChipInfo> entry : session.getPlacedChips().entrySet()) {
            Location loc = entry.getKey();
            ArenaSession.PlacedChipInfo info = entry.getValue();
            Block block = loc.getBlock();
            if (block.getType().name().contains("CARPET")) {
                block.setType(info.getOriginalBlockOrAir());
                playChipBreakEffect(loc);
            }
        }
    }

    /**
     * チップ消滅時の視覚・音響エフェクトを再生する。
     *
     * @param location エフェクトの再生座標
     */
    private void playChipBreakEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 0.1, 0.5);
        world.spawnParticle(Particle.CLOUD, center, 8, 0.3, 0.1, 0.3, 0.02);
        world.spawnParticle(Particle.CRIT, center, 5, 0.2, 0.1, 0.2, 0.05);
        world.playSound(center, Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
    }
}
