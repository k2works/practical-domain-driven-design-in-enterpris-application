package com.example.cargotracker.identity.application.internal.commandservices;

/**
 * TOTP の確かめの結果（Bolt 31、US-18 AC1）。拒否の種類は監査記録の理由にだけ使い、利用者には同じ文言を示す（AC2）。
 */
public enum TotpVerification {
    /** 通った。その刻みを使ったことにした。 */
    ACCEPTED,
    /** 前後 1 つの刻みのどのコードとも一致しない（形の違うコードを含む）。 */
    REJECTED_MISMATCH,
    /** 使った刻み以下のコード（同じコードの 2 回目や、同時の送信の後のほう）。 */
    REJECTED_REUSED,
    /** TOTP を登録していない。 */
    REJECTED_NOT_ENROLLED;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
