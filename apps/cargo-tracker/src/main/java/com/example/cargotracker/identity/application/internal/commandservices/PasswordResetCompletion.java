package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import java.util.Objects;

/**
 * password の再設定の結果（{@link PasswordResetCompletionService}。Bolt 33a）。画面は種類で行き先と文言を選ぶ。
 */
public sealed interface PasswordResetCompletion {

    /**
     * 新しい password を設定した（トークンを使い、session の失効まで終えた）。
     *
     * @param initial 招待のリンクで初回の password を設定したか（画面は完了の知らせを選ぶ。Bolt 38b4）
     */
    record Completed(boolean initial) implements PasswordResetCompletion {}

    /** リンクが使えない（期限切れ・使用済み・無効・存在しない・利用停止。どれかを示さない）。トークンも password も変えていない。 */
    record Invalid() implements PasswordResetCompletion {}

    /**
     * 新しい password が方針に合わない。トークンは使っていない（直して送り直せる）。
     *
     * @param violation 合わない理由
     */
    record PolicyViolated(PasswordPolicyViolation violation) implements PasswordResetCompletion {

        public PolicyViolated {
            Objects.requireNonNull(violation, "violation");
        }
    }
}
