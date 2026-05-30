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
        session.addOrUpdateBet(player.getUniqueId(), teamName, chipValue);
        session.addPlacedChip(location, player.getUniqueId(), teamName, chipValue, originalBlock);

        // 固定オッズ方式の場合、オッズをロック
        String payoutMethod = plugin.getConfig().getString("payout-method", "pari-mutuel");
        if ("fixed-odds".equals(payoutMethod)) {
            double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);
            Bet bet = session.getBet(player.getUniqueId());
            if (bet != null) {
                double currentOdds = plugin.getPayoutStrategy().calculateOdds(session, teamName, houseEdge);
                bet.setLockedOdds(currentOdds);
            }
        }

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "✔ " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に "
                + ChatColor.YELLOW + ChipManager.formatAmount(chipValue) + " E"
                + ChatColor.GREEN + " を賭けました！"
                + ChatColor.GRAY + " (合計: "
                + ChipManager.formatAmount(session.getBet(player.getUniqueId()).getAmount()) + " E)");

        return true;
    }

    /**
     * 設置カーペットの回収（賭け取消）を処理する。
     *
     * @param session  セッション
     * @param player   回収者
     * @param location 回収座標
     * @return 取消に成功した場合 {@code true}
     */
    public boolean cancelBet(ArenaSession session, Player player, Location location) {
        ArenaSession.PlacedChipInfo chipInfo = session.getPlacedChip(location);
        if (chipInfo == null) return false;

        // 自分の賭けのみ取消可能
        if (!chipInfo.getPlayerId().equals(player.getUniqueId())) return false;

        session.removePlacedChip(location);

        // 賭け金額の更新（減算）
        Bet bet = session.getBet(player.getUniqueId());
        if (bet != null) {
            bet.addAmount(-chipInfo.getChipValue());
        }

        // 視覚エフェクト: 回収演出
        playChipBreakEffect(location);

        player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                + "賭けを " + ChipManager.formatAmount(chipInfo.getChipValue())
                + " E 分取り消しました。");

        return true;
    }

    /**
     * オッズをブロードキャストする。
     *
     * @param session セッション
     */
    public void broadcastOdds(ArenaSession session) {
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);
        PayoutStrategy strategy = plugin.getPayoutStrategy();
        long totalPool = session.getTotalPool();

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "📊 現在のオッズ:");
        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = ArenaMessages.getTeamColor(i);
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
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);
        PayoutStrategy strategy = plugin.getPayoutStrategy();
        Map<UUID, Long> payouts = strategy.calculatePayouts(session, winningTeam, houseEdge);

        long totalPool = session.getTotalPool();
        long totalPayout = 0;

        // 設置されたカーペットを全削除
        clearPlacedChips(session);

        // 配当配布
        ChipManager chipManager = plugin.getChipManager();
        for (Map.Entry<UUID, Long> entry : payouts.entrySet()) {
            UUID playerId = entry.getKey();
            long payout = entry.getValue();
            totalPayout += payout;

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // チップとして配布
                Map<Chip, Integer> chips = chipManager.breakdownAmount(payout);
                int slotsNeeded = chipManager.calculateSlotsNeeded(chips);
                int emptySlots = chipManager.countEmptySlots(player);

                chipManager.giveChips(player, chips);

                if (emptySlots < slotsNeeded) {
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                            + "⚠️ インベントリがいっぱいため、一部のチップが足元にドロップしました。");
                }

                long originalBet = session.getBet(playerId).getAmount();
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
            }
        }

        // 負けた人への通知
        for (Bet bet : session.getBets().values()) {
            if (!bet.getTeamName().equals(winningTeam)) {
                Player player = Bukkit.getPlayer(bet.getPlayerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "残念… 賭け金 " + ChatColor.YELLOW
                            + ChipManager.formatAmount(bet.getAmount()) + " E"
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
        ChipManager chipManager = plugin.getChipManager();

        // 設置カーペットを全削除
        clearPlacedChips(session);

        // 賭け者に返金（チップとして）
        for (Bet bet : session.getBets().values()) {
            Player player = Bukkit.getPlayer(bet.getPlayerId());
            if (player != null && player.isOnline() && bet.getAmount() > 0) {
                Map<Chip, Integer> chips = chipManager.breakdownAmount(bet.getAmount());
                int slotsNeeded = chipManager.calculateSlotsNeeded(chips);
                int emptySlots = chipManager.countEmptySlots(player);

                chipManager.giveChips(player, chips);

                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "賭け金 " + ChatColor.WHITE
                        + ChipManager.formatAmount(bet.getAmount()) + " E"
                        + ChatColor.YELLOW + " をチップで返金しました。");

                if (emptySlots < slotsNeeded) {
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                            + "⚠️ インベントリがいっぱいため、一部の返金チップが足元にドロップしました。");
                }
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
