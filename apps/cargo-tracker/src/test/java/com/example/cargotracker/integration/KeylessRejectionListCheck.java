package com.example.cargotracker.integration;

import java.util.concurrent.Callable;

/**
 * 行 21 の一覧の照会の判定（Bolt 40）。却下のトランザクションの間に照会した一覧が空だったとき、照会の前に却下がコミットを終えただけなら
 * 失敗にしない。却下が終わらないのに空になるのは、トランザクションの外で行が消えた異常なので失敗にする。時刻にも Spring にも依存しない。
 */
final class KeylessRejectionListCheck {

    /** 一覧が空だったときに、却下の完了を待つ上限（秒）。 */
    static final long COMPLETION_WAIT_SECONDS = 5;

    private KeylessRejectionListCheck() {}

    /**
     * @param listWasEmpty 照会した一覧が空だったか
     * @param awaitCompletion 却下の完了を待つ関数（{@link #COMPLETION_WAIT_SECONDS} 秒まで。完了しなければ例外を投げる）
     */
    static void assertListedDuringRejection(boolean listWasEmpty, Callable<?> awaitCompletion) {
        if (!listWasEmpty) {
            return;
        }
        try {
            awaitCompletion.call();
        } catch (Exception e) {
            throw new AssertionError(
                    "一覧が空なのに、却下が " + COMPLETION_WAIT_SECONDS + " 秒たっても完了していない（却下のトランザクションの外で行が消えた）", e);
        }
    }
}
