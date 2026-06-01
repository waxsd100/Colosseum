package io.wax100.arenaCore.model;

/**
 * 試合モードを表す列挙型。
 *
 * <ul>
 *   <li>{@link #NORMAL} — 通常試合: 観客ベット中心、闘技者は固定給＋還元</li>
 *   <li>{@link #DEATHMATCH} — デスマッチ: 闘技者同士の高額合意マッチ</li>
 * </ul>
 */
public enum MatchMode {
    /** 通常試合: 観客ベット中心、闘技者は固定給＋還元 */
    NORMAL("通常試合"),
    /** デスマッチ: 闘技者同士の高額合意マッチ */
    DEATHMATCH("デスマッチ");

    private final String displayName;

    MatchMode(String displayName) {
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
