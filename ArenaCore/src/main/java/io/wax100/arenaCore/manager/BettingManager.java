package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.BettingRegion;
import io.wax100.arenaCore.model.MatchMode;
import io.wax100.arenaCore.payout.PayoutDistributor;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.BalanceDisplay;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ベット管理マネージャ。
 *
 * <p>物理カーペット設置ベースのベットを処理する:
 * <ul>
 *   <li>カーペット設置時のベット記録</li>
 *   <li>カーペット回収時のベット取消</li>
 *   <li>オッズのブロードキャスト</li>
 *   <li>天引き分配と配当計算</li>
 *   <li>ジャックポット積立・発動</li>
 *   <li>全額返金（キャンセル時）</li>
 * </ul>
 */
public class BettingManager {

    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 70;
    private static final int TITLE_FADE_OUT = 20;

    private final ArenaCore plugin;

    public BettingManager(ArenaCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    }

    // ── Config helper ──

    /**
     * 天引き分配率をコンフィグから一括読み込みする。
     */
    private record DistributionRates(double loserShare, double winnerShare, double houseFee, double bettorShareRate) {
        static DistributionRates fromConfig(ArenaCore plugin) {
            double loser = plugin.getConfig().getDouble("distribution.loser-fighter-share", 0.01);
            double winner = plugin.getConfig().getDouble("distribution.winner-fighter-share", 0.10);
            double house = plugin.getConfig().getDouble("distribution.house-fee", 0.05);
            double bettor = 1.0 - loser - winner - house;
            return new DistributionRates(loser, winner, house, bettor);
        }
    }

    // ── Public API ──

    /**
     * カーペットチップの設置をベットとして処理する。
     *
     * @param session   セッション
     * @param player    設置者
     * @param teamName  ベット先チーム
     * @param chipValue チップ額面
     * @param location  設置座標
     * @return ベットとして記録された場合 {@code true}
     */
    public boolean placeBet(ArenaSession session, Player player, String teamName,
                            long chipValue, Location location, Material originalBlock) {
        // ベット記録
        try {
            session.addOrUpdateBet(player.getUniqueId(), teamName, chipValue);
        } catch (IllegalStateException e) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + e.getMessage());
            return false;
        }
        session.addPlacedChip(location, player.getUniqueId(), teamName, chipValue, originalBlock);

        ChatColor teamColor = session.getTeamColor(teamName);

        String msg = ChatColor.GREEN + "✔ " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に "
                + ChatColor.YELLOW + ChipManager.formatAmount(chipValue) + " E"
                + ChatColor.GREEN + " ベット"
                + ChatColor.GRAY + " (合計: "
                + ChipManager.formatAmount(session.getBet(player.getUniqueId(), teamName).amount()) + " E)";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));

        return true;
    }

    /**
     * 設置カーペットの回収（ベット取消）を処理する。
     *
     * @param session  セッション
     * @param player   回収者
     * @param location 回収座標
     */
    public void cancelBet(ArenaSession session, Player player, Location location) {
        ArenaSession.PlacedChipInfo chipInfo = session.getPlacedChip(location);
        if (chipInfo == null) return;

        // 自分のベットのみ取消可能
        if (!chipInfo.playerId().equals(player.getUniqueId())) return;

        session.removePlacedChip(location);

        // カーペットブロックを除去（ドロップ防止のため直接AIRに設定）
        location.getBlock().setType(Material.AIR);

        // ベット額の更新（減算）
        Bet bet = session.getBet(player.getUniqueId(), chipInfo.teamName());
        if (bet != null) {
            if (bet.amount() >= chipInfo.chipValue()) {
                bet.addAmount(-chipInfo.chipValue());
                // 金額が0以下になった場合はベット自体を削除
                if (bet.amount() <= 0) {
                    session.removeBet(player.getUniqueId(), chipInfo.teamName());
                }
            } else {
                // データ不整合 — ベット自体を削除
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
     * パリミュチュエル方式でオッズをブロードキャストする。
     *
     * @param session セッション
     */
    public void broadcastOdds(ArenaSession session) {
        long totalPool = session.getTotalPool();
        boolean isBlind = session.getState() == ArenaState.BLIND;

        // 分配率を読み込んでベッター配当プール比率を計算
        DistributionRates rates = DistributionRates.fromConfig(plugin);

        if (isBlind) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                    + "📊 現在のオッズ: " + ChatColor.GRAY + "(ブラインド中 — オッズ非公開)");
        } else {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "📊 現在のオッズ:");
        }

        List<String> teamNames = session.getTeamNames();
        for (String team : teamNames) {
            ChatColor color = session.getTeamColor(team);
            long teamPool = session.getTeamPool(team);

            if (isBlind) {
                // ブラインド中はオッズ・プール金額を隠す
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                        + ChatColor.GRAY + " | " + ChatColor.WHITE + "???"
                        + ChatColor.GRAY + " | プール: "
                        + ChatColor.YELLOW + "??? E");
            } else {
                // パリミュチュエルオッズ: (totalPool * bettorShareRate) / teamPool
                String oddsStr;
                if (teamPool > 0 && totalPool > 0) {
                    double bettorPool = totalPool * rates.bettorShareRate();
                    double odds = bettorPool / teamPool;
                    oddsStr = String.format("%.2f倍", odds);
                } else {
                    oddsStr = "---";
                }

                Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                        + ChatColor.GRAY + " | " + ChatColor.WHITE + oddsStr
                        + ChatColor.GRAY + " | プール: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(teamPool) + " E");
            }
        }

        if (isBlind) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "  総プール: " + ChatColor.YELLOW + "??? E");
        } else {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "  総プール: " + ChatColor.YELLOW + ChipManager.formatAmount(totalPool) + " E");
        }
    }

    /**
     * パリミュチュエルオッズを計算する（表示用）。
     *
     * @param session  セッション
     * @param teamName チーム名
     * @return オッズ倍率。ベットがない場合は 0.0
     */
    public double calculateOdds(ArenaSession session, String teamName) {
        long totalPool = session.getTotalPool();
        long teamPool = session.getTeamPool(teamName);

        if (totalPool <= 0 || teamPool <= 0) return 0.0;

        DistributionRates rates = DistributionRates.fromConfig(plugin);

        double bettorPool = totalPool * rates.bettorShareRate();
        return bettorPool / teamPool;
    }

    /**
     * 天引き分配方式で配当を計算し配布する。
     *
     * <p>分配フロー:
     * <ol>
     *   <li>最低保証金を全闘技者に支給（NORMALモードのみ）</li>
     *   <li>敗者闘技者に還元（ベッタープールの一定%）</li>
     *   <li>勝者闘技者に還元（ベッタープールの一定%）</li>
     *   <li>ジャックポット発動判定 → 発動時は積立金 + 手数料を勝利ベッターに分配</li>
     *   <li>通常時は手数料をジャックポットに積立</li>
     *   <li>勝利ベッターにパリミュチュエル方式で配当</li>
     *   <li>勝利ベッターがいない場合は観客配当プールをジャックポットに積立</li>
     *   <li>entry-fee をジャックポットに積立</li>
     * </ol>
     *
     * @param session     セッション
     * @param winningTeam 勝利チーム名
     */
    public void calculateAndDistributePayout(ArenaSession session, String winningTeam) {
        PayoutDistributor distributor = plugin.getPayoutDistributor();
        JackpotManager jackpot = plugin.getJackpotManager();

        long totalPool = session.getTotalPool();

        // 設置されたカーペットを全削除
        clearPlacedChips(session);

        // ── 最低保証金を全闘技者に支給（NORMALモードのみ） ──
        distributeGuarantee(session);

        // ── ベッタープール0時の処理 ──
        if (totalPool == 0) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "ベット額がなかったため配当はありません。");

            // entry-fee をジャックポットに積立
            long entryFeePool = session.getEntryFeePool();
            if (entryFeePool > 0 && jackpot != null) {
                jackpot.deposit(entryFeePool);
            }
            return;
        }

        // ── 天引き分配計算 ──
        DistributionRates rates = DistributionRates.fromConfig(plugin);

        PayoutDistributor.DistributionResult dist =
                PayoutDistributor.calculate(totalPool, rates.loserShare(), rates.winnerShare(), rates.houseFee());

        // ── 闘技者への還元 ──
        distributeFighterShares(session, winningTeam, dist);

        // ── ジャックポット発動判定 ──
        long winnerTeamBets = session.getTeamPool(winningTeam);
        boolean jackpotEnabled = plugin.getConfig().getBoolean("jackpot.enabled", true);
        double threshold = plugin.getConfig().getDouble("jackpot.trigger-threshold", 0.10);
        boolean jackpotTriggered = false;
        long jackpotBonus = 0;

        if (jackpot != null && jackpotEnabled
                && jackpot.shouldTrigger(winnerTeamBets, totalPool, threshold)) {
            jackpotTriggered = true;
            // ジャックポット全額引き出し + 今回の手数料
            jackpotBonus = jackpot.withdrawAll() + dist.houseFee();
        } else if (jackpot != null && jackpotEnabled) {
            // 通常時: 手数料をジャックポットに積立
            jackpot.deposit(dist.houseFee());
        }

        // ── 勝利ベッターへの配当 ──
        long bettorPool = dist.bettorPayoutPool() + (jackpotTriggered ? jackpotBonus : 0);
        long totalPayout = distributeWinnerBetPayouts(session, winningTeam, bettorPool,
                jackpotTriggered, jackpotBonus);

        // ── 負けた人への通知 ──
        notifyLosers(session, winningTeam);

        // ── entry-fee をジャックポットに積立 ──
        long entryFeePool = session.getEntryFeePool();
        if (entryFeePool > 0 && jackpot != null) {
            jackpot.deposit(entryFeePool);
        }

        // ── デスマッチプール分配 ──
        distributeDeathmatchPool(session, winningTeam);

        // ── 結果サマリー ──
        broadcastPayoutSummary(session, winningTeam, dist, totalPayout);
    }

    /**
     * 全ベット額を返金する（キャンセル時）。
     *
     * @param session セッション
     */
    public void refundAll(ArenaSession session) {
        // 設置カーペットを全削除
        clearPlacedChips(session);

        // ベッターに返金（チップとして）
        for (Bet bet : session.getAllBets()) {
            if (bet.amount() <= 0) continue;

            distributeAmount(bet.playerId(), bet.amount());

            Player player = Bukkit.getPlayer(bet.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "ベット額 " + ChatColor.WHITE
                        + ChipManager.formatAmount(bet.amount()) + " E"
                        + ChatColor.YELLOW + " をチップで返金しました。");
            } else {
                plugin.getLogger().warning("オフラインプレイヤーへの返金をVault経由で実行: "
                        + bet.playerId() + " / " + bet.amount() + " E");
            }
        }
    }

    // ── calculateAndDistributePayout 分解メソッド ──

    /**
     * 最低保証金を全闘技者に支給する。
     */
    private void distributeGuarantee(ArenaSession session) {
        long guarantee = plugin.getConfig().getLong("fighter-guarantee", 100);
        if (guarantee > 0) {
            for (String teamName : session.getTeamNames()) {
                for (UUID fighterId : session.getTeamMembers(teamName)) {
                    Player fighter = Bukkit.getPlayer(fighterId);
                    if (fighter != null) {
                        distributeAmount(fighterId, guarantee);
                        fighter.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                                + "💰 最低保証金: " + ChatColor.YELLOW
                                + ChipManager.formatAmount(guarantee) + " E");
                        notifyBalanceDelta(fighter, guarantee);
                    }
                }
            }
        }
    }

    /**
     * 敗者闘技者・勝者闘技者への還元を行う。
     */
    private void distributeFighterShares(ArenaSession session, String winningTeam,
                                         PayoutDistributor.DistributionResult dist) {
        // ── 敗者闘技者への還元 ──
        if (dist.loserFighterTotal() > 0) {
            List<UUID> loserFighters = getPlayerFighters(session, winningTeam, false);
            if (!loserFighters.isEmpty()) {
                distributeFighterPool(loserFighters, dist.loserFighterTotal(), "敗者還元", "💸");
            }
        }

        // ── 勝者闘技者への還元 ──
        if (dist.winnerFighterTotal() > 0) {
            List<UUID> winnerFighters = getPlayerFighters(session, winningTeam, true);
            if (!winnerFighters.isEmpty()) {
                distributeFighterPool(winnerFighters, dist.winnerFighterTotal(), "勝者還元", "🏆");
            }
        }
    }

    /**
     * 闘技者プールを等分配布し、端数をジャックポットに積立する。
     *
     * @param fighters 配布対象の闘技者UUID一覧
     * @param pool     配布プール額
     * @param label    メッセージラベル（例: "敗者還元"）
     * @param emoji    メッセージ絵文字（例: "💸"）
     * @return ジャックポットに積立された端数
     */
    private long distributeFighterPool(List<UUID> fighters, long pool, String label, String emoji) {
        JackpotManager jackpot = plugin.getJackpotManager();
        long perFighter = pool / fighters.size();
        if (perFighter > 0) {
            ChatColor labelColor = label.contains("敗者") ? ChatColor.YELLOW : ChatColor.GREEN;
            for (UUID fighterId : fighters) {
                distributeAmount(fighterId, perFighter);
                Player fighter = Bukkit.getPlayer(fighterId);
                if (fighter != null && fighter.isOnline()) {
                    fighter.sendMessage(ArenaMessages.PREFIX + labelColor
                            + emoji + " " + label + ": " + ChipManager.formatAmount(perFighter) + " E");
                    notifyBalanceDelta(fighter, perFighter);
                }
            }
        }
        // 端数をジャックポットに積立
        long remainder = pool - (perFighter * fighters.size());
        if (remainder > 0 && jackpot != null) {
            jackpot.deposit(remainder);
        }
        return remainder;
    }

    /**
     * 勝利ベッターにパリミュチュエル方式で配当を配布する。
     *
     * @param session          セッション
     * @param winningTeam      勝利チーム名
     * @param bettorPool       観客配当プール（ジャックポットボーナス込み）
     * @param jackpotTriggered ジャックポットが発動したか
     * @param jackpotBonus     ジャックポットボーナス額
     * @return 配当合計
     */
    private long distributeWinnerBetPayouts(ArenaSession session, String winningTeam,
                                            long bettorPool, boolean jackpotTriggered,
                                            long jackpotBonus) {
        JackpotManager jackpot = plugin.getJackpotManager();
        Map<UUID, Long> bettorPayouts = calculatePariMutuelPayouts(session, winningTeam, bettorPool);

        long totalPayout = 0;

        if (bettorPayouts.isEmpty()) {
            // 勝利ベッターがいない → 観客配当プールをジャックポットに積立
            if (jackpot != null) {
                jackpot.deposit(bettorPool);
            }
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "勝利チームにベットした人がいないため、配当はジャックポットに積立されました。");
        } else {
            // ジャックポット演出
            if (jackpotTriggered) {
                playJackpotEffects(bettorPayouts, jackpotBonus);
            }

            // 配当配布
            for (Map.Entry<UUID, Long> entry : bettorPayouts.entrySet()) {
                UUID playerId = entry.getKey();
                long payout = entry.getValue();
                totalPayout += payout;

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    Bet playerBet = session.getBet(playerId, winningTeam);
                    if (playerBet == null) continue;
                    long originalBet = playerBet.amount();
                    long profit = payout - originalBet;

                    // ダブルアップ選択を提示（配布前に判定）
                    DoubleUpManager doubleUp = plugin.getDoubleUpManager();
                    if (doubleUp != null && doubleUp.offerChoice(playerId, payout, originalBet, winningTeam)) {
                        // ダブルアップ選択待ち — 配当はまだ配布しない
                        continue;
                    }

                    // ダブルアップ不参加 → 通常配布
                    distributeAmount(playerId, payout);

                    // 配当計算式の表示
                    long totalPool = session.getTotalPool();
                    long winningPool = session.getTeamPool(winningTeam);
                    double odds = winningPool > 0 ? (double) bettorPool / winningPool : 0;

                    player.sendMessage("");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "🎉 配当受取！");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "───────────────────");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "総ベット額: "
                            + ChatColor.WHITE + ChipManager.formatAmount(totalPool) + " E");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "勝利チームプール: "
                            + ChatColor.WHITE + ChipManager.formatAmount(winningPool) + " E");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "配当プール: "
                            + ChatColor.WHITE + ChipManager.formatAmount(bettorPool) + " E"
                            + ChatColor.DARK_GRAY + " (天引後)");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "オッズ: "
                            + ChatColor.AQUA + String.format("×%.2f", odds)
                            + ChatColor.DARK_GRAY + " (" + ChipManager.formatAmount(bettorPool)
                            + " ÷ " + ChipManager.formatAmount(winningPool) + ")");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "───────────────────");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "あなたのベット: "
                            + ChatColor.YELLOW + ChipManager.formatAmount(originalBet) + " E");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "計算: "
                            + ChatColor.WHITE + ChipManager.formatAmount(originalBet)
                            + ChatColor.GRAY + " × "
                            + ChatColor.AQUA + String.format("%.2f", odds)
                            + ChatColor.GRAY + " = "
                            + ChatColor.YELLOW + ChatColor.BOLD + ChipManager.formatAmount(payout) + " E");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "損益: "
                            + (profit >= 0 ? ChatColor.GREEN + "+"
                            : ChatColor.RED.toString())
                            + ChipManager.formatAmount(profit) + " E");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "───────────────────");
                    player.sendMessage("");

                    notifyBalanceDelta(player, payout);
                } else {
                    distributeAmount(playerId, payout);
                    plugin.getLogger().warning("オフラインプレイヤーへの配当をVault経由で入金: " + playerId + " / " + payout + " E");
                }
            }
        }

        return totalPayout;
    }

    /**
     * 負けたベッターに没収通知を送信する。
     */
    private void notifyLosers(ArenaSession session, String winningTeam) {
        for (Bet bet : session.getAllBets()) {
            if (!bet.teamName().equals(winningTeam)) {
                Player player = Bukkit.getPlayer(bet.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "残念… ベット額 " + ChatColor.YELLOW
                            + ChipManager.formatAmount(bet.amount()) + " E"
                            + ChatColor.RED + " は没収されました。");
                    player.sendMessage("");

                    notifyBalanceDelta(player, -bet.amount());

                    // ダブルアップ保留中なら没収
                    DoubleUpManager doubleUp = plugin.getDoubleUpManager();
                    if (doubleUp != null && doubleUp.hasActiveStreak(player.getUniqueId())) {
                        doubleUp.confiscateOnLoss(player.getUniqueId());
                    }
                }
            }
        }
    }

    /**
     * デスマッチプールを勝者闘技者に分配する。
     */
    private void distributeDeathmatchPool(ArenaSession session, String winningTeam) {
        JackpotManager jackpot = plugin.getJackpotManager();
        long dmPoolDistributed = 0;

        if (session.getMatchMode() == MatchMode.DEATHMATCH && session.getDeathmatchPool() > 0) {
            long dmPool = session.getDeathmatchPool();
            long dmHouseFee = 0;

            boolean dmHouseFeeEnabled = plugin.getConfig().getBoolean("deathmatch.house-fee-enabled", false);
            if (dmHouseFeeEnabled) {
                double dmHouseFeeRate = plugin.getConfig().getDouble("distribution.house-fee", 0.05);
                dmHouseFee = (long) Math.floor(dmPool * dmHouseFeeRate);
                if (dmHouseFee > 0 && jackpot != null) {
                    jackpot.deposit(dmHouseFee);
                }
            }

            long dmPayout = dmPool - dmHouseFee;
            List<UUID> winnerFightersDm = getPlayerFighters(session, winningTeam, true);

            if (!winnerFightersDm.isEmpty() && dmPayout > 0) {
                long perFighter = dmPayout / winnerFightersDm.size();
                if (perFighter > 0) {
                    for (UUID fighterId : winnerFightersDm) {
                        distributeAmount(fighterId, perFighter);
                        dmPoolDistributed += perFighter;
                        Player fighter = Bukkit.getPlayer(fighterId);
                        if (fighter != null && fighter.isOnline()) {
                            fighter.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                                    + "🔥 デスマッチ報酬: " + ChipManager.formatAmount(perFighter) + " E");
                        }
                    }
                }
                // DM端数をジャックポットに積立
                long dmRemainder = dmPayout - (perFighter * winnerFightersDm.size());
                if (dmRemainder > 0 && jackpot != null) {
                    jackpot.deposit(dmRemainder);
                }
            }

            if (dmPoolDistributed > 0 || dmHouseFee > 0) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                        + "🔥 デスマッチプール: " + ChatColor.YELLOW
                        + ChipManager.formatAmount(dmPool) + " E"
                        + ChatColor.GRAY + " → 勝者闘技者に分配: " + ChatColor.YELLOW
                        + ChipManager.formatAmount(dmPoolDistributed) + " E");
            }
        }
    }

    /**
     * 配当結果サマリーをブロードキャストする。
     */
    private void broadcastPayoutSummary(ArenaSession session, String winningTeam,
                                        PayoutDistributor.DistributionResult dist,
                                        long totalPayout) {
        JackpotManager jackpot = plugin.getJackpotManager();
        long totalPool = dist.loserFighterTotal() + dist.winnerFighterTotal()
                + dist.houseFee() + dist.bettorPayoutPool();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "━━━ 配当結果 ━━━");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "ベット総額: " + ChatColor.WHITE + ChipManager.formatAmount(totalPool) + " E");

        // チーム別内訳
        for (String team : session.getTeamNames()) {
            ChatColor color = session.getTeamColor(team);
            long teamPool = session.getTeamPool(team);
            String marker = team.equals(winningTeam) ? ChatColor.GREEN + "✔ " : ChatColor.RED + "✘ ";
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + marker + color + team
                    + ChatColor.GRAY + ": " + ChatColor.YELLOW + ChipManager.formatAmount(teamPool) + " E");
        }

        // 天引き内訳
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "敗者闘技者還元: " + ChatColor.YELLOW
                + ChipManager.formatAmount(dist.loserFighterTotal()) + " E"
                + ChatColor.GRAY + " / 勝者闘技者還元: " + ChatColor.YELLOW
                + ChipManager.formatAmount(dist.winnerFighterTotal()) + " E");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "運営手数料: " + ChatColor.YELLOW
                + ChipManager.formatAmount(dist.houseFee()) + " E"
                + ChatColor.GRAY + " / 観客配当: " + ChatColor.GREEN
                + ChipManager.formatAmount(totalPayout) + " E");

        if (jackpot != null) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "🎰 ジャックポット残高: " + ChatColor.YELLOW
                    + ChipManager.formatAmount(jackpot.getBalance()) + " E");
        }
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage("");
    }

    // ── 内部ヘルパーメソッド ──

    /**
     * 指定チームのプレイヤー闘技者（Mob以外）のUUIDリストを返す。
     *
     * @param session      セッション
     * @param winningTeam  勝利チーム名
     * @param isWinnerSide true の場合は勝利チーム、false の場合は敗者チーム
     * @return プレイヤー闘技者のUUIDリスト
     */
    private List<UUID> getPlayerFighters(ArenaSession session, String winningTeam, boolean isWinnerSide) {
        List<UUID> fighters = new ArrayList<>();
        for (String teamName : session.getTeamNames()) {
            boolean isWinnerTeam = teamName.equals(winningTeam);
            if (isWinnerSide != isWinnerTeam) continue;

            // Mob チームのメンバーもプレイヤーとして含まれている可能性があるが、
            // Mob の UUID はサーバー上に Player として存在しないため Bukkit.getPlayer() で null になる
            for (UUID memberId : session.getTeamMembers(teamName)) {
                // プレイヤーとして解決可能な UUID のみ対象
                if (Bukkit.getPlayer(memberId) != null || Bukkit.getOfflinePlayer(memberId).hasPlayedBefore()) {
                    fighters.add(memberId);
                }
            }
        }
        return fighters;
    }

    /**
     * パリミュチュエル方式で勝利ベッターへの配当を計算する。
     *
     * @param session     セッション
     * @param winningTeam 勝利チーム名
     * @param bettorPool  観客配当プール
     * @return 各プレイヤーの配当金額マップ (UUID → 配当額)
     */
    private Map<UUID, Long> calculatePariMutuelPayouts(ArenaSession session, String winningTeam, long bettorPool) {
        Map<UUID, Long> payouts = new HashMap<>();
        long winningPool = session.getTeamPool(winningTeam);

        if (bettorPool <= 0 || winningPool <= 0) return payouts;

        double odds = (double) bettorPool / winningPool;

        for (Bet bet : session.getAllBets()) {
            if (bet.teamName().equals(winningTeam)) {
                long payout = Math.max(0L, (long) Math.floor(bet.amount() * odds));
                payouts.merge(bet.playerId(), payout, Long::sum);
            }
        }

        return payouts;
    }

    /**
     * ジャックポット発動時の演出を再生する。
     *
     * @param bettorPayouts 勝利ベッターへの配当マップ
     * @param jackpotAmount ジャックポット額
     */
    private void playJackpotEffects(Map<UUID, Long> bettorPayouts, long jackpotAmount) {
        // 全体通知
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD.toString() + ChatColor.BOLD
                + "═══════════════════════════════");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "🎰 JACKPOT!! 🎰");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                + "ジャックポット " + ChipManager.formatAmount(jackpotAmount) + " E が配当に加算！");
        Bukkit.broadcastMessage(ChatColor.GOLD.toString() + ChatColor.BOLD
                + "═══════════════════════════════");
        Bukkit.broadcastMessage("");

        // 勝利ベッターにタイトル＆花火
        for (UUID playerId : bettorPayouts.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendTitle(
                        ChatColor.GOLD.toString() + ChatColor.BOLD + "🎰 JACKPOT!! 🎰",
                        ChatColor.YELLOW + ChipManager.formatAmount(jackpotAmount) + " E",
                        TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                // 花火
                spawnFirework(player.getLocation());
            }
        }
    }

    /**
     * 花火エフェクトをスポーンする。
     *
     * @param location スポーン座標
     */
    private void spawnFirework(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Firework firework = world.spawn(location.clone().add(0, 1, 0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.RED)
                .withFlicker()
                .withTrail()
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
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
        if (amount <= 0) return;
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
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(offlinePlayer, amount);
            } else {
                plugin.getLogger().severe("Vault経済APIが利用不可: プレイヤー " + playerId + " への " + amount + " E が喪失しました");
            }
        }
    }

    /**
     * 外部（DoubleUpManager等）から呼び出し可能な配布メソッド。
     *
     * @param playerId 配布先プレイヤーのUUID
     * @param amount   配布額
     */
    public void payoutToPlayer(UUID playerId, long amount) {
        distributeAmount(playerId, amount);
    }

    /**
     * 設置されたカーペットチップを全てワールドから削除する。
     *
     * <p>2段階で確実に除去する:
     * <ol>
     *   <li>placedChips マップに登録された座標を AIR に置換</li>
     *   <li>ベットエリア全体をスキャンし、残存カーペットも AIR に置換</li>
     * </ol>
     *
     * @param session セッション
     */
    private void clearPlacedChips(ArenaSession session) {
        // ① 登録済みチップ座標を AIR に
        for (Map.Entry<Location, ArenaSession.PlacedChipInfo> entry : session.getPlacedChips().entrySet()) {
            Location loc = entry.getKey();
            Block block = loc.getBlock();
            if (block.getType().name().contains("CARPET")) {
                block.setType(Material.AIR, false);
                playChipBreakEffect(loc);
            }
        }

        // ② ベットエリア全体をスキャンし、残存カーペットも除去
        RegionManager regionManager = plugin.getRegionManager();
        for (String team : session.getTeamNames()) {
            BettingRegion region = regionManager.getBettingRegion(team);
            if (region == null) continue;
            World world = Bukkit.getWorld(region.worldName());
            if (world == null) continue;
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType().name().contains("CARPET")) {
                            block.setType(Material.AIR, false);
                        }
                    }
                }
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
    /**
     * ChipLib の BalanceDisplay に音付き差額通知を送る。
     *
     * @param player 対象プレイヤー
     * @param amount 変動額（正: 収入, 負: 支出）
     */
    private void notifyBalanceDelta(Player player, long amount) {
        ChipPlugin chipPlugin = (ChipPlugin) Bukkit.getPluginManager().getPlugin("ChipLib");
        if (chipPlugin == null) return;
        BalanceDisplay display = chipPlugin.getBalanceDisplay();
        if (display == null) return;
        display.notifyDelta(player, amount);
    }
}
