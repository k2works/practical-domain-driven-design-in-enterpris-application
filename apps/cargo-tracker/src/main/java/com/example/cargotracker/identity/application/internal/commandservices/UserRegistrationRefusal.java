package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import java.util.Objects;

/**
 * 利用者の登録を受け付けない理由（{@link UserRegistrationService}。Bolt 38b2）。役割の変更・利用状態の変更の {@link UserAdministrationRefusal}
 * と分ける（対象が企業で、版がなく、企業の無効と重複のメールアドレスが加わる）。「企業がない」と「管理範囲の外」は別の値にし、画面が同じ
 * 応答にする（存在を示さない。Bolt 34b の前例）。
 */
public sealed interface UserRegistrationRefusal {

    /** 操作者がシステム管理者でない。 */
    record NotPermitted() implements UserRegistrationRefusal {}

    /** 企業がない。 */
    record CompanyNotFound() implements UserRegistrationRefusal {}

    /** 企業が操作者の管理範囲の外（無効な企業も、範囲の外ならこれ）。確定ではこの試行を記録した。 */
    record CompanyOutOfScope() implements UserRegistrationRefusal {}

    /** 企業が無効（管理範囲の内）。 */
    record InactiveCompany() implements UserRegistrationRefusal {}

    /** 表示名が空・長すぎる・制御文字を含む。 */
    record InvalidDisplayName() implements UserRegistrationRefusal {}

    /**
     * 登録の判定（{@code User#decideRegistration}）が受け付けない。
     *
     * @param rejection 理由と示す役割
     */
    record Rejected(UserRegistrationRejection rejection) implements UserRegistrationRefusal {

        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** 同じメールアドレスの利用者が既にいる（管理範囲の内外を問わず同じ。Bolt 38b の開始準備の決定 (2)）。 */
    record DuplicateEmail() implements UserRegistrationRefusal {}
}
