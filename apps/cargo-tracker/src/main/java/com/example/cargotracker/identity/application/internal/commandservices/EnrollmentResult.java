package com.example.cargotracker.identity.application.internal.commandservices;

import java.util.List;

/** TOTP の登録を完了しようとした結果（Bolt 31b、US-18 AC7、SEC-09）。 */
public sealed interface EnrollmentResult {

    /**
     * 登録した。回復コードの平文はこの結果で一度だけ示し、どこにも保存しない（ハッシュだけを保存した）。
     *
     * @param recoveryCodes 回復コードの平文
     */
    record Enrolled(List<String> recoveryCodes) implements EnrollmentResult {

        public Enrolled {
            recoveryCodes = List.copyOf(recoveryCodes);
        }

        @Override
        public String toString() {
            return "Enrolled[recoveryCodes=***]";
        }
    }

    /** 最初のコードが前後 1 つの刻みのどれとも一致しない（保存していない。同じ秘密で示し直す）。 */
    record CodeRejected() implements EnrollmentResult {}

    /** 既に登録している（A-02 へ送る）。 */
    record AlreadyEnrolled() implements EnrollmentResult {}

    /** 登録の途中の暗号文を使えない（別の利用者の暗号文、壊れた暗号文、知らない鍵の版）。登録をやり直す。 */
    record SecretUnusable() implements EnrollmentResult {}
}
