package io.wax100.chipLib;

import java.util.UUID;

/**
 * チップ換金時に呼び出されるコールバックインターフェース。
 *
 * <p>外部プラグイン（CasinoCore 等）がこのインターフェースを実装し、
 * {@link ChipPlugin#setCashoutListener(ChipCashoutListener)} で登録することで、
 * {@code /chip cashout} による換金時に購入記録の相殺などの処理を行える。
 */
@FunctionalInterface
public interface ChipCashoutListener {

    /**
     * チップ換金が成功した際に呼び出される。
     *
     * @param playerId    換金プレイヤーの UUID
     * @param totalValue  換金合計額
     */
    void onCashout(UUID playerId, long totalValue);
}
