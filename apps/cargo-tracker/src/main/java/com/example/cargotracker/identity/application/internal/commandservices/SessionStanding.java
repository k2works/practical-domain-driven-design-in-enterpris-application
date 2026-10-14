package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.Objects;

/**
 * request ごとの確かめの結果（Bolt 32c、US-18 AC6、IA-INV-04）。今の主体か、session を捨てる理由のどちらか。
 */
public sealed interface SessionStanding {

    /**
     * 続けてよい。今の役割・表示名・企業名の主体。
     *
     * @param actor 今の値で作った主体
     * @param email ログインのメールアドレス（認証の主体の名前に使う）
     */
    record Current(AuthenticatedActor actor, EmailAddress email) implements SessionStanding {

        public Current {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(email, "email");
        }
    }

    /**
     * session を捨てる。監査記録 {@code SESSION_REVOKED} は書いた。
     *
     * @param reason 利用停止・無効な企業・利用者なし
     */
    record Revoked(AuthenticationRejection reason) implements SessionStanding {

        public Revoked {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
