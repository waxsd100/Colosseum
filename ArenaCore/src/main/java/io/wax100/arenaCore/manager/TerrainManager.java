package io.wax100.arenaCore.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 3段階地形復元マネージャ。
 *
 * <p>試合中のブロック破壊を追跡し、3段階で地形を復元する:
 * <ol>
 *   <li><b>Stage 1</b> — 試合中: N tick後に個別復元（ゆっくり）</li>
 *   <li><b>Stage 2</b> — 試合後: 高速復元（1tickにN個）</li>
 *   <li><b>Stage 3</b> — Schematic ペーストで差分ゼロ保証</li>
 * </ol>
 *
 * <p>クラッシュ復旧は {@code .active} マーカーファイルと
 * {@code .schem} ファイルの組み合わせで実現する。
 */
public class TerrainManager {

    // ── 状態 ──

    enum State { IDLE, TRACKING, FLUSHING }

    private State state = State.IDLE;

    // ── データ ──

    private final Deque<RestoreEntry> restoreQueue = new ArrayDeque<>();
    private BukkitTask duringMatchTask;
    private BukkitTask flushTask;
    private ArenaFieldConfig fieldConfig;
    private String sessionName;
    private long tickCounter = 0;
    private BukkitTask tickCounterTask;

    // ── config値 ──

    private boolean enabled;
    private final boolean worldEditAvailable;
    private int duringMatchDelay;
    private int postMatchDelay;
    private int postMatchBlocksPerTick;
    private boolean effects;

    private final ArenaCore plugin;

    // ── 内部クラス ──

    /**
     * 復元対象の情報を保持するデータクラス。
     */
    static final class RestoreEntry {
        final Location location;
        final BlockData originalData;
        final long restoreAtTick;

        RestoreEntry(Location location, BlockData originalData, long restoreAtTick) {
            this.location = location;
            this.originalData = originalData;
            this.restoreAtTick = restoreAtTick;
        }
    }

    // ══════════════════════════════════════
    //  初期化
    // ══════════════════════════════════════

    /**
     * TerrainManager を初期化する。
     *
     * @param plugin ArenaCore プラグインインスタンス
     */
    public TerrainManager(ArenaCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        loadConfig();

        // WorldEdit 存在チェック
        this.worldEditAvailable = plugin.getServer()
                .getPluginManager().getPlugin("WorldEdit") != null;
        if (enabled && !worldEditAvailable) {
            plugin.getLogger().warning(
                    "terrain-restore.enabled=true ですが WorldEdit がありません。"
                    + "地形復元を無効化します。");
            enabled = false;
        }
    }

    /**
     * config.yml から地形復元設定を読み込む。
     */
    private void loadConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("terrain-restore.enabled", true);
        duringMatchDelay = Math.max(0,
                config.getInt("terrain-restore.during-match-delay", 300));
        postMatchDelay = config.getInt("terrain-restore.post-match-delay", 60);
        postMatchBlocksPerTick = Math.max(1,
                config.getInt("terrain-restore.post-match-blocks-per-tick", 10));
        effects = config.getBoolean("terrain-restore.effects", true);
    }

    // ══════════════════════════════════════
    //  Schematic 保存（/arena field set 時）
    // ══════════════════════════════════════

    /**
     * フィールド範囲を WorldEdit Schematic として保存する。
     *
     * <p>{@code plugins/ArenaCore/arenas/<arenaName>.schem} に保存される。
     * 保存済みファイルは削除せず、毎試合で使い回す。
     *
     * @param field     戦闘エリア設定
     * @param arenaName アリーナ名
     */
    public void saveFieldSchematic(ArenaFieldConfig field, String arenaName) {
        World bukkitWorld = field.getWorld();
        if (bukkitWorld == null) {
            plugin.getLogger().severe("ワールドが見つかりません: "
                    + field.getWorldName());
            return;
        }
        com.sk89q.worldedit.world.World weWorld =
                BukkitAdapter.adapt(bukkitWorld);
        CuboidRegion region = field.toRegion();

        try (EditSession editSession =
                     WorldEdit.getInstance().newEditSession(weWorld)) {
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(region.getMinimumPoint());
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    editSession, region, clipboard,
                    region.getMinimumPoint());
            Operations.complete(copy);

            File file = new File(plugin.getDataFolder(),
                    "arenas/" + arenaName + ".schem");
            file.getParentFile().mkdirs();

            // SPONGE_V3_SCHEMATIC を直接使用
            try (ClipboardWriter writer =
                         BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                                 .getWriter(new FileOutputStream(file))) {
                writer.write(clipboard);
            }
            plugin.getLogger().info("Schematic保存完了: " + arenaName
                    + " (" + field.getBlockCount() + "ブロック)");
        } catch (Exception e) {
            plugin.getLogger().severe("Schematic保存失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════
    //  Schematic 復元
    // ══════════════════════════════════════

    /**
     * 保存済み Schematic をペーストする。
     *
     * <p>ワールド名を明示的に受け取る（.schem にはワールド情報がないため）。
     * {@code .schem} ファイルは削除しない（次回も使い回す）。
     *
     * @param arenaName アリーナ名
     * @param worldName ワールド名
     */
    public void pasteSchematic(String arenaName, String worldName) {
        File file = new File(plugin.getDataFolder(),
                "arenas/" + arenaName + ".schem");
        if (!file.exists()) {
            plugin.getLogger().warning(
                    "Schematic未発見のため復旧スキップ: " + file.getPath());
            return;
        }
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            plugin.getLogger().severe("ワールド未ロード: " + worldName);
            return;
        }
        com.sk89q.worldedit.world.World weWorld =
                BukkitAdapter.adapt(bukkitWorld);

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().severe("Schematicフォーマット不明: " + file.getPath());
            return;
        }

        try (ClipboardReader reader =
                     format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            try (EditSession editSession =
                         WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(clipboard.getOrigin())
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(op);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Schematic復元失敗: " + e.getMessage());
        }
        // .schem は削除しない（次回も使い回す）
    }

    // ══════════════════════════════════════
    //  Stage 1: 試合中の追跡 + ゆっくり復元
    // ══════════════════════════════════════

    /**
     * 地形追跡を開始する。
     *
     * <p>試合開始時に呼ばれる。tickCounter を初期化し、
     * {@code .active} マーカーファイルを作成する。
     *
     * @param session 現在のアリーナセッション
     */
    public void startTracking(ArenaSession session) {
        if (!enabled) {
            plugin.getLogger().info("地形復元は無効です。");
            return;
        }
        fieldConfig = session.getFieldConfig();
        if (fieldConfig == null) {
            plugin.getLogger().warning(ArenaMessages.MSG_TERRAIN_NO_FIELD);
            return;
        }
        sessionName = session.getName();
        restoreQueue.clear();
        tickCounter = 0;
        state = State.TRACKING;

        // 自前tickカウンター開始（Spigot互換、Paper API不要）
        tickCounterTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // .active マーカーファイル作成
        writeActiveMarker(sessionName, fieldConfig.getWorldName());

        Bukkit.broadcastMessage(ArenaMessages.MSG_TERRAIN_TRACKING);
    }

    /**
     * ブロック破壊を記録する。
     *
     * <p>TRACKING 状態かつフィールド範囲内の場合のみキューに追加する。
     * キューが空から非空になった場合、復元タスクを起動する。
     *
     * @param loc      破壊されたブロックの座標
     * @param original 破壊前のブロックデータ
     */
    public void recordBreak(Location loc, BlockData original) {
        if (state != State.TRACKING) return;
        if (fieldConfig == null || !fieldConfig.contains(loc)) return;

        long restoreAt = tickCounter + duringMatchDelay;
        restoreQueue.add(new RestoreEntry(loc, original, restoreAt));

        // キュー空→非空: タスク起動
        if (duringMatchTask == null || duringMatchTask.isCancelled()) {
            duringMatchTask = new BukkitRunnable() {
                @Override
                public void run() {
                    processDuringMatch();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    /**
     * 試合中の復元処理。delay が経過したエントリを1個ずつ復元する。
     * キューが空になったらタスクを停止する。
     */
    private void processDuringMatch() {
        while (!restoreQueue.isEmpty()) {
            RestoreEntry entry = restoreQueue.peek();
            if (entry.restoreAtTick > tickCounter) break;
            restoreQueue.poll();
            restoreSingleBlock(entry);
        }
        // キューが空になったらタスク停止
        if (restoreQueue.isEmpty() && duringMatchTask != null) {
            duringMatchTask.cancel();
            duringMatchTask = null;
        }
    }

    /**
     * 1ブロックを復元する（変更がある場合のみ）。
     *
     * @param entry 復元エントリ
     */
    private void restoreSingleBlock(RestoreEntry entry) {
        // ワールドがアンロードされている場合はスキップ
        if (entry.location.getWorld() == null) return;

        Block block = entry.location.getBlock();
        if (!block.getBlockData().equals(entry.originalData)) {
            block.setBlockData(entry.originalData, false);
            if (effects) playEffect(entry.location, entry.originalData);
        }
    }

    // ══════════════════════════════════════
    //  Stage 2 → 3: 試合後の復元
    // ══════════════════════════════════════

    /**
     * 試合終了後の復元を開始する。
     *
     * <p>試合中タスクを停止し、Stage 2（高速復元）を開始する。
     * Stage 2 完了後、自動的に Stage 3（Schematic ペースト）が実行される。
     *
     * <p>TerrainManager は sessionName/fieldConfig を独自保持しているため、
     * 呼び出し元で activeSession=null になっても問題ない。
     */
    public void finishAndFlush() {
        if (state != State.TRACKING) return;

        // タスク停止
        if (duringMatchTask != null) {
            duringMatchTask.cancel();
            duringMatchTask = null;
        }
        if (tickCounterTask != null) {
            tickCounterTask.cancel();
            tickCounterTask = null;
        }

        state = State.FLUSHING;
        Bukkit.broadcastMessage(ArenaMessages.MSG_TERRAIN_FLUSHING);

        // Stage 2: 高速復元 → 完了後 Stage 3
        flushTask = new TerrainRestoreTask(
                restoreQueue, this, postMatchBlocksPerTick, effects)
                .runTaskTimer(plugin, postMatchDelay, 1L);
    }

    /**
     * Stage 2（高速復元）完了時に {@link TerrainRestoreTask} から呼ばれる。
     *
     * <p>Stage 3 として Schematic をペーストし、状態をクリアする。
     */
    void onFlushComplete() {
        // Stage 3: Schematic ペースト（差分ゼロ保証）
        pasteSchematic(sessionName, fieldConfig.getWorldName());

        // .active マーカー削除
        deleteActiveMarker(sessionName);

        // 状態クリア
        state = State.IDLE;
        fieldConfig = null;
        sessionName = null;
        restoreQueue.clear();

        Bukkit.broadcastMessage(ArenaMessages.MSG_TERRAIN_COMPLETE);
    }

    // ══════════════════════════════════════
    //  クラッシュ復旧
    // ══════════════════════════════════════

    /**
     * サーバー起動時のクラッシュ復旧を行う。
     *
     * <p>onEnable から {@code runTaskLater(1L)} で呼ばれる。
     * {@code arenas/} ディレクトリの {@code .active} ファイルを走査し、
     * 対応する {@code .schem} が存在すればペーストして復旧する。
     */
    public void checkCrashRecovery() {
        File arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) return;

        File[] activeFiles = arenasDir.listFiles(
                (dir, name) -> name.endsWith(".active"));
        if (activeFiles == null) return;

        for (File activeFile : activeFiles) {
            YamlConfiguration yaml =
                    YamlConfiguration.loadConfiguration(activeFile);
            String arenaName = yaml.getString("arena");
            String worldName = yaml.getString("world");

            if (arenaName == null || worldName == null) {
                plugin.getLogger().warning(
                        "不正な.activeファイルを削除: " + activeFile.getName());
                activeFile.delete();
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning(
                        "クラッシュ復旧スキップ (ワールド未ロード): "
                        + worldName);
                continue; // .active は残す（次回再試行）
            }

            File schemFile = new File(arenasDir, arenaName + ".schem");
            if (!schemFile.exists()) {
                plugin.getLogger().warning(
                        "クラッシュ復旧: Schematic未発見のためスキップ: "
                        + arenaName);
                activeFile.delete();
                continue;
            }

            plugin.getLogger().info(
                    "クラッシュ復旧開始: " + arenaName + " (" + worldName + ")");
            pasteSchematic(arenaName, worldName);
            activeFile.delete();
            plugin.getLogger().info("クラッシュ復旧完了: " + arenaName);
        }
    }

    // ══════════════════════════════════════
    //  マーカーファイル操作
    // ══════════════════════════════════════

    /**
     * {@code .active} マーカーファイルを作成する。
     *
     * @param arenaName アリーナ名
     * @param worldName ワールド名
     */
    private void writeActiveMarker(String arenaName, String worldName) {
        File file = new File(plugin.getDataFolder(),
                "arenas/" + arenaName + ".active");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("arena", arenaName);
        yaml.set("world", worldName);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "マーカーファイル作成失敗: " + e.getMessage());
        }
    }

    /**
     * {@code .active} マーカーファイルを削除する。
     *
     * @param arenaName アリーナ名
     */
    private void deleteActiveMarker(String arenaName) {
        new File(plugin.getDataFolder(),
                "arenas/" + arenaName + ".active").delete();
    }

    // ══════════════════════════════════════
    //  ユーティリティ
    // ══════════════════════════════════════

    /**
     * 地形復元中（IDLE 以外）かどうかを返す。
     *
     * <p>復元中はセッション作成をブロックするために使用する。
     *
     * @return 復元処理中の場合 {@code true}
     */
    public boolean isBlocking() {
        return state != State.IDLE;
    }


    /**
     * すべてのタスクをキャンセルし、状態をクリアする。
     *
     * <p>サーバー停止時（{@code onDisable}）に呼ばれる。
     * {@code .active} マーカーは残す（次回 onEnable で復旧するため）。
     */
    public void cancelAndClear() {
        if (duringMatchTask != null) duringMatchTask.cancel();
        if (flushTask != null) flushTask.cancel();
        if (tickCounterTask != null) tickCounterTask.cancel();
        restoreQueue.clear();
        fieldConfig = null;
        sessionName = null;
        tickCounter = 0;
        // .active は残す → 次回 onEnable で復旧
        state = State.IDLE;
    }

    /**
     * 復元エフェクト（パーティクル + 効果音）を再生する。
     *
     * <p>package-private: {@link TerrainRestoreTask} からアクセス可能。
     *
     * @param loc  エフェクトの座標
     * @param data ブロックデータ（パーティクル用）
     */
    void playEffect(Location loc, BlockData data) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.BLOCK_CRACK, center,
                8, 0.3, 0.3, 0.3, 0, data);
        world.playSound(center, Sound.BLOCK_STONE_PLACE, 0.5f, 1.2f);
    }
}
