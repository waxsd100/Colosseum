package io.wax100.arenaCore.model;

/**
 * 闘技場セッションの状態遷移を表す列挙型。
 *
 * <pre>
 * SETUP → BETTING → CLOSED → ACTIVE → FINISHED
 *           ↑                    ↓
 *           └── (cancel: any → FINISHED)
 * </pre>
 *
 * <p>状態遷移の検証は {@link ArenaSession} の {@code setState()} メソッドで行われる。
 */
public enum ArenaState {
    /** セットアップ中: チーム編成・エリア設定 */
    SETUP("セットアップ中"),
    /** 賭け受付中: 観客がカーペットを設置して賭ける */
    BETTING("賭け受付中"),
    /** 賭け締切: 賭け受付終了・試合開始待ち */
    CLOSED("賭け締切"),
    /** 試合中: 戦闘進行・賭け締切 */
    ACTIVE("試合中"),
    /** 終了: 配当処理済み */
    FINISHED("終了");

    private final String displayName;

    ArenaState(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 日本語表示名を返す。
     *
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}
