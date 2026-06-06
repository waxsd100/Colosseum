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
import io.wax100.arenaCore.storage.BlockRestoreEntry;
import io.wax100.arenaCore.storage.MemoryTerrainStorage;
import io.wax100.arenaCore.storage.TerrainStorageProvider;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * Redis ストレージ使用時は {@link TerrainStorageProvider#getPendingSessions()} も
 * クラッシュ復旧の情報源として活用する。
 */
public class TerrainManager {

    private static final Particle BLOCK_PARTICLE;

    static {
        Particle p;
        try {
            p = Particle.valueOf("BLOCK");
        } catch (IllegalArgumentException e) {
            p = Particle.valueOf("BLOCK_CRACK");
        }
        BLOCK_PARTICLE = p;
    }

    // ── 状態 ──

    enum State { IDLE, TRACKING, FLUSHING }

    private State state = State.IDLE;

    // ── データ ──

    private final TerrainStorageProvider terrainStorage;
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

    // ══════════════════════════════════════
    //  初期化
    // ══════════════════════════════════════

    /**
     * TerrainManager を初期化する。
     *
     * @param plugin          ArenaCore プラグインインスタンス
     * @param terrainStorage  地形復元ストレージプロバイダ
     */
    public TerrainManager(ArenaCore plugin, TerrainStorageProvider terrainStorage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.terrainStorage = Objects.requireNonNull(terrainStorage, "terrainStorage must not be null");
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
                    + field.worldName());
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

            // WE バージョンにより SPONGE_V3_SCHEMATIC / SPONGE_SCHEMATIC が異なる
            ClipboardFormat schemFormat = resolveSchematicFormat();
            try (ClipboardWriter writer =
                         schemFormat.getWriter(new FileOutputStream(file))) {
                writer.write(clipboard);
            }
            plugin.getLogger().info("Schematic保存完了: " + arenaName
                    + " (" + field.getBlockCount() + "ブロック)");
        } catch (Exception e) {
            plugin.getLogger().severe("Schematic保存失敗: " + e.getMessage());
        }
    }

    /**
     * WorldEdit バージョンに応じた Schematic 書き出しフォーマットを解決する。
     *
     * <p>SPONGE_V3_SCHEMATIC → SPONGE_SCHEMATIC の順にフォールバックする。
     *
     * @return 利用可能な {@link ClipboardFormat}
     */
    private static ClipboardFormat resolveSchematicFormat() {
        for (BuiltInClipboardFormat fmt : BuiltInClipboardFormat.values()) {
            String name = fmt.name();
            if (name.contains("SPONGE")) {
                return fmt;
            }
        }
        // フォールバック: 拡張子から検出
        ClipboardFormat fallback = ClipboardFormats.findByAlias("sponge");
        if (fallback != null) return fallback;
        // 最終手段: enum の先頭を返す
        return BuiltInClipboardFormat.values()[0];
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
        sessionName = session.getId().toString();
        terrainStorage.clearSession(sessionName);
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
        writeActiveMarker(sessionName, fieldConfig.worldName());

        Bukkit.broadcastMessage(ArenaMessages.MSG_TERRAIN_TRACKING);
    }

    /**
     * ブロック破壊を記録する。
     *
     * <p>TRACKING 状態かつフィールド範囲内の場合のみストレージに追加する。
     *
     * @param loc      破壊されたブロックの座標
     * @param original 破壊前のブロックデータ
     */
    public void recordBreak(Location loc, BlockData original) {
        if (!isTrackable(loc)) return;

        terrainStorage.recordBlockChange(sessionName,
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                original.getAsString(),
                tickCounter + duringMatchDelay);
        ensureDuringMatchTask();
    }

    /**
     * ブロック設置を記録する。
     *
     * <p>設置ブロックは試合中には復元せず、試合後（Stage 2/3）でのみ復元する。
     *
     * @param loc          設置されたブロックの座標
     * @param previousData 設置前のブロックデータ（通常は AIR）
     */
    public void recordPlace(Location loc, BlockData previousData) {
        if (!isTrackable(loc)) return;

        // Long.MAX_VALUE → Stage 1 では処理されず、Stage 2 の flush で一括復元
        terrainStorage.recordBlockChange(sessionName,
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                previousData.getAsString(),
                Long.MAX_VALUE);
    }

    /** TRACKING 中かつフィールド範囲内かを判定する。 */
    private boolean isTrackable(Location loc) {
        return state == State.TRACKING && fieldConfig != null && fieldConfig.contains(loc);
    }

    /** 試合中復元タスクが停止していれば起動する。 */
    private void ensureDuringMatchTask() {
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
     * 試合中の復元処理。delay が経過したエントリを復元する。
     * ストレージが空になったらタスクを停止する。
     */
    private void processDuringMatch() {
        List<BlockRestoreEntry> readyEntries =
                terrainStorage.pollReadyEntries(sessionName, tickCounter);
        for (BlockRestoreEntry entry : readyEntries) {
            restoreSingleBlock(entry);
        }
        // ストレージが空になったらタスク停止
        if (terrainStorage.isEmpty(sessionName) && duringMatchTask != null) {
            duringMatchTask.cancel();
            duringMatchTask = null;
        }
    }

    /**
     * 1ブロックを復元する（変更がある場合のみ）。
     *
     * @param entry 復元エントリ
     */
    private void restoreSingleBlock(BlockRestoreEntry entry) {
        World world = Bukkit.getWorld(entry.worldName());
        // ワールドがアンロードされている場合はスキップ
        if (world == null) return;

        Location loc = new Location(world, entry.x(), entry.y(), entry.z());
        BlockData originalData = Bukkit.createBlockData(entry.blockDataString());
        Block block = loc.getBlock();
        if (!block.getBlockData().equals(originalData)) {
            block.setBlockData(originalData, false);
            if (effects) playEffect(loc, originalData);
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

        cancelTrackingTasks();
        state = State.FLUSHING;
        Bukkit.broadcastMessage(ArenaMessages.MSG_TERRAIN_FLUSHING);

        // MemoryTerrainStorage の場合は設置ブロックを先頭に並べ替える
        if (terrainStorage instanceof MemoryTerrainStorage memoryStorage) {
            memoryStorage.reorderPlacedBlocksFirst(sessionName);
        }
        // Redis の場合は Sorted Set が score 順（設置ブロック = Double.MAX_VALUE が末尾）のため、
        // ZPOPMIN で自然に score 昇順で取り出される。並べ替え不要。

        // Stage 2: 高速復元 → Stage 3
        flushTask = new TerrainRestoreTask(
                terrainStorage, this, sessionName, postMatchBlocksPerTick, effects)
                .runTaskTimer(plugin, postMatchDelay, 1L);
    }

    /** 試合中タスクを全て停止する。 */
    private void cancelTrackingTasks() {
        if (duringMatchTask != null) {
            duringMatchTask.cancel();
            duringMatchTask = null;
        }
        if (tickCounterTask != null) {
            tickCounterTask.cancel();
            tickCounterTask = null;
        }
    }

    /**
     * Stage 2（高速復元）完了時に {@link TerrainRestoreTask} から呼ばれる。
     *
     * <p>Stage 3 として Schematic をペーストし、状態をクリアする。
     */
    void onFlushComplete() {
        // Stage 3: Schematic ペースト（差分ゼロ保証）
        pasteSchematic(sessionName, fieldConfig.worldName());

        // .active マーカー削除
        deleteActiveMarker(sessionName);

        // ストレージのセッションデータをクリア
        terrainStorage.clearSession(sessionName);

        // 状態クリア
        state = State.IDLE;
        fieldConfig = null;
        sessionName = null;

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
     *
     * <p>Redis ストレージ使用時は {@link TerrainStorageProvider#getPendingSessions()} で
     * アクティブセッションを追加チェックし、Redis 側のデータもクリアする。
     */
    public void checkCrashRecovery() {
        File arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) arenasDir.mkdirs();

        // ── .active ファイルベースの復旧 ──
        File[] activeFiles = arenasDir.listFiles(
                (dir, name) -> name.endsWith(".active"));
        if (activeFiles != null) {
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

        // ── Redis ストレージの保留セッションをクリア ──
        Set<String> pendingSessions = terrainStorage.getPendingSessions();
        for (String pendingSessionId : pendingSessions) {
            plugin.getLogger().info(
                    "Redis 保留セッションをクリア: " + pendingSessionId);
            terrainStorage.clearSession(pendingSessionId);
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
        cancelTrackingTasks();
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (sessionName != null) {
            terrainStorage.clearSession(sessionName);
        }
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
        world.spawnParticle(BLOCK_PARTICLE, center,
                8, 0.3, 0.3, 0.3, 0, data);
        world.playSound(center, Sound.BLOCK_STONE_PLACE, 0.5f, 1.2f);
    }
}
