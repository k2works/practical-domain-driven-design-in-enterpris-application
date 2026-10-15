package com.example.cargotracker.identity.application.internal.commandservices;

import java.util.List;

/** 認証アプリの再登録を完了しようとした結果（Bolt 33b、US-18 AC9、SEC-10）。 */
public sealed interface ReEnrollmentResult {

    /**
     * 登録し直した。新しい回復コードの平文はこの結果で一度だけ示し、どこにも保存しない（ハッシュだけを保存した）。
     *
     * @param recoveryCodes 新しい回復コードの平文
     */
    record ReEnrolled(List<String> recoveryCodes) implements ReEnrollmentResult {

        public ReEnrolled {
            recoveryCodes = List.copyOf(recoveryCodes);
        }

        @Override
        public String toString() {
            return "ReEnrolled[recoveryCodes=***]";
        }
    }

    /** 新しいコードが前後 1 つの刻みのどれとも一致しない（置き換えていない。同じ秘密で示し直す）。 */
    record CodeRejected() implements ReEnrollmentResult {}

    /** 始めてから 15 分を過ぎた（置き換えていない。新しい秘密でやり直す）。 */
    record Expired() implements ReEnrollmentResult {}

    /** 再登録の途中の暗号文を使えない（別の利用者の暗号文、壊れた暗号文、知らない鍵の版）。やり直す。 */
    record SecretUnusable() implements ReEnrollmentResult {}

    /** ほかの画面・二度押しで、先に登録し直していた（置き換えも回復コードの発行し直しもしていない）。 */
    record Superseded() implements ReEnrollmentResult {}

    /** 再登録できない（資格がない・利用停止・企業の無効。どれかは示さない）。 */
    record Unavailable() implements ReEnrollmentResult {}
}
