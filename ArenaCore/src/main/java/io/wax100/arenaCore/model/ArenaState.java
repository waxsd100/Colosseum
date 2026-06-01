package io.wax100.arenaCore.model;

/**
 * 闘技場セッションの状態遷移を表す列挙型。
 *
 * <pre>
 * SETUP → RECRUITING → BETTING → BLIND → CLOSED → ACTIVE → FINISHED
 *                                                              ↓
 *                        (cancel: any → FINISHED)
 * </pre>
 *
 * <p>状態遷移の検証は {@link ArenaSession} の {@code setState()} メソッドで行われる。
 */
public enum ArenaState {
    /** セットアップ中: チーム編成・エリア設定 */
    SETUP("セットアップ中"),
    /** 参加者募集中: 闘技者がエリアに入って参加 */
    RECRUITING("参加者募集中"),
    /** ベット受付中: 参加者確定＆観客がカーペットを設置してベットする */
    BETTING("ベット受付中"),
    /** ブラインド: ベット可能だがオッズ非公開 */
    BLIND("ブラインド"),
    /** ベット締切: ベット受付終了・試合モード確定・試合開始待ち */
    CLOSED("ベット締切"),
    /** 試合中: 戦闘進行 */
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
