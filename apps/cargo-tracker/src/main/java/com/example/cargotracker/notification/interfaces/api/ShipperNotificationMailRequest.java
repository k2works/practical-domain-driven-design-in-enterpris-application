package com.example.cargotracker.notification.interfaces.api;

import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import java.util.Objects;
import java.util.UUID;

/**
 * 荷主への業務の通知の要求（通知の公開 API。ADR-017。US-22。Bolt 43）。値は Java の標準の型と文字列だけ。本文は件名と画面への導線だけで、料金・契約条件・
 * 書類・他の予約の情報を載せない（AC3、N-INV-01）ので、要求にもそれらを持たない。
 *
 * @param eventId 契機（イベント）の ID（通知の {@code event_id}。同じ契機と宛先で冪等）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param kind 通知の種類
 * @param targetRef 対象の参照（業務番号など。通知の {@code target_ref}）
 * @param link 画面への導線の URL（公開の base URL と画面のパス）
 * @param locale 文面の言語（BCP 47。今は {@code ja} だけ）
 */
public record ShipperNotificationMailRequest(
        UUID eventId,
        UUID recipientUserId,
        String email,
        ShipperNotificationKind kind,
        String targetRef,
        String link,
        String locale) {

    public ShipperNotificationMailRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetRef, "targetRef");
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない（ログや例外の文に載せない）。 */
    @Override
    public String toString() {
        return "ShipperNotificationMailRequest[eventId=" + eventId + ", recipientUserId=" + recipientUserId + ", kind="
                + kind + ", targetRef=" + targetRef + ", locale=" + locale + "]";
    }
}
