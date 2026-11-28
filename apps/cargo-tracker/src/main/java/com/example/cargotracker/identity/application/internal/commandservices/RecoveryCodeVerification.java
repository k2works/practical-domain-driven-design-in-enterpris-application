package com.example.cargotracker.identity.application.internal.commandservices;

/**
 * 回復コードによるログインの確かめの結果（Bolt 31c、US-18 AC9 のログインの分）。拒否の種類は分けない（使用済みのコードは
 * 未使用の一覧に出ないので、誤りと区別できない）。利用者には TOTP の誤りと同じ文言を示す（AC2）。
 */
public enum RecoveryCodeVerification {
    /** 通った。そのコードを使ったことにし、監査記録を書いた。 */
    ACCEPTED,
    /** 形が違う、未使用のどのコードとも一致しない、同時の使用で後になった、回復コードがない。 */
    REJECTED;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
