package io.wax100.chipLib;

import java.util.UUID;

/**
 * チップ購入時に呼び出されるコールバックインターフェース。
 *
 * <p>外部プラグイン（CasinoCore 等）がこのインターフェースを実装し、
 * {@link ChipPlugin#setPurchaseListener(ChipPurchaseListener)} で登録することで、
 * チップ購入時に購入記録などの処理を行える。
 */
@FunctionalInterface
public interface ChipPurchaseListener {

    /**
     * チップ購入が成功した際に呼び出される。
     *
     * @param playerId  購入プレイヤーの UUID
     * @param totalCost 購入合計額
     */
    void onPurchase(UUID playerId, long totalCost);
}
