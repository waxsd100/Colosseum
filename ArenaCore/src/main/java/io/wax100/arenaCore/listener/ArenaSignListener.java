package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.sub.OpenSubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.ArenaPresetStore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 開始看板リスナー。
 *
 * <p>管理者が看板の1行目に {@code [arena]}（または {@code [闘技場]}）、
 * 2行目にプリセット名を書くと「開始看板」になる。
 * 一般プレイヤーでも右クリックするだけでプリセットをロードし、
 * 参加者募集（Open）を開始できる。
 *
 * <ul>
 *   <li>看板の作成には {@code arenacore.admin} 権限が必要</li>
 *   <li>クリックによる開始は権限不要（誰でも可）</li>
 *   <li>セッション稼働中・地形復元中は開始できない</li>
 *   <li>看板起動のセッションは無人進行（自動締切・自動開始）となり、
 *       終了後にオートループは発動しない</li>
 * </ul>
 */
public class ArenaSignListener implements Listener {

    /** 看板作成時に受け付けるタグ（小文字比較） */
    private static final String INPUT_TAG_EN = "[arena]";
    private static final String INPUT_TAG_JP = "[闘技場]";

    /** フォーマット済み1行目（クリック判定にも使用） */
    private static final String SIGN_TAG =
            ChatColor.DARK_RED + "[" + ChatColor.RED + "闘技場" + ChatColor.DARK_RED + "]";

    private final ArenaCore plugin;

    public ArenaSignListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * 開始看板の作成を処理する。
     *
     * <p>1行目がタグの場合、権限とプリセット名を検証し、
     * 有効なら看板を装飾フォーマットに書き換える。
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0);
        if (line0 == null) return;
        String tag = line0.trim().toLowerCase();
        if (!tag.equals(INPUT_TAG_EN) && !tag.equals(INPUT_TAG_JP)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("arenacore.admin")) {
            event.setCancelled(true);
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "開始看板を作成する権限がありません。");
            return;
        }

        String presetName = event.getLine(1) != null ? event.getLine(1).trim() : "";
        if (presetName.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "2行目にプリセット名を入力してください。");
            return;
        }
        if (plugin.getPresetStore().load(presetName) == null) {
            event.setCancelled(true);
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "プリセットが見つかりません: " + presetName);
            return;
        }

        event.setLine(0, SIGN_TAG);
        event.setLine(1, presetName);
        event.setLine(2, ChatColor.DARK_GREEN + "右クリックで");
        event.setLine(3, ChatColor.DARK_GREEN + "募集開始");
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + "開始看板を作成しました: " + presetName);
    }

    /**
     * 開始看板の右クリックを処理する。
     *
     * <p>プリセットをロードしてセッションを作成し、参加者募集を開始する。
     * 開始に失敗した場合（TP先未設定等）はセッションを破棄して再試行可能にする。
     */
    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // オフハンドの二重発火防止
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;
        if (!"[闘技場]".equals(ChatColor.stripColor(sign.getLine(0)))) return;

        // 1.20 の看板編集UIを開かせない
        event.setCancelled(true);

        Player player = event.getPlayer();
        String presetName = ChatColor.stripColor(sign.getLine(1)).trim();
        if (presetName.isEmpty()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "看板にプリセット名が設定されていません。");
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        if (manager.hasActiveSession()) {
            ArenaState state = manager.getActiveSession().getState();
            if (state == ArenaState.RECRUITING) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                        + "募集中です。チーム待機エリアに入って参加してください。");
            } else {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "現在開催中です。終了までお待ちください。");
            }
            return;
        }
        if (plugin.getTerrainManager().isBlocking()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "地形復元中です。しばらくお待ちください。");
            return;
        }

        ArenaPresetStore.PresetData data = plugin.getPresetStore().load(presetName);
        if (data == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "プリセットが見つかりません: " + presetName);
            return;
        }

        ArenaSession session = manager.createFromPreset(data);
        if (session == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "セッションを作成できませんでした。");
            return;
        }
        manager.setLastPresetName(presetName);

        // 無人進行モードを有効化（募集→締切→試合開始まで自動。終了後のオートループには接続しない）
        manager.setAutoRunSession(true);

        int duration = manager.resolveAutoRunDuration();
        String[] args = new String[]{String.valueOf(duration)};
        new OpenSubCommand(plugin).execute(player, args);

        // 開始に失敗した場合（TP先未設定等）はセッションを破棄して再試行可能にする
        if (manager.hasActiveSession() && manager.getActiveSession().getState() == ArenaState.SETUP) {
            manager.cancelArena();
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "開始できませんでした。管理者に連絡してください。");
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + player.getName() + " が看板から募集を開始しました。");
    }
}
