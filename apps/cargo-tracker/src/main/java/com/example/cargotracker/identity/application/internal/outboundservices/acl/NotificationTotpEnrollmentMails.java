package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMail;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailRequest;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知の認証アプリの登録と再登録のお知らせ。腐敗防止層で、通知の公開 API（{@link TotpEnrollmentMail}）を呼び、結果の型をこの層の外に出さない
 * （ADR-017。Bolt 38d）。宛先は identity が渡す（通知は identity に依存しない）。
 */
public class NotificationTotpEnrollmentMails {

    /** 文面の言語（今は日本語だけ。UI 設計）。 */
    static final String LOCALE = "ja";

    private final TotpEnrollmentMail totpEnrollmentMail;

    public NotificationTotpEnrollmentMails(TotpEnrollmentMail totpEnrollmentMail) {
        this.totpEnrollmentMail = totpEnrollmentMail;
    }

    /**
     * 登録（A-07）のお知らせを送る。監査記録の ID と宛先で冪等。
     *
     * @param auditRecordId 登録の監査記録の ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param enrolledAt 登録の時刻
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> sendEnrolled(UUID auditRecordId, UserId userId, EmailAddress email, UtcInstant enrolledAt) {
        return send(auditRecordId, userId, email, enrolledAt, false);
    }

    /**
     * 回復コードの経路の再登録（A-08）のお知らせを送る。監査記録の ID と宛先で冪等。
     *
     * @param auditRecordId 再登録の監査記録の ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param reEnrolledAt 再登録の時刻
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> sendReEnrolled(
            UUID auditRecordId, UserId userId, EmailAddress email, UtcInstant reEnrolledAt) {
        return send(auditRecordId, userId, email, reEnrolledAt, true);
    }

    private Optional<String> send(
            UUID auditRecordId, UserId userId, EmailAddress email, UtcInstant enrolledAt, boolean reEnrolled) {
        TotpEnrollmentMailReceipt receipt = totpEnrollmentMail.send(new TotpEnrollmentMailRequest(
                auditRecordId, userId.value(), email.value(), enrolledAt, reEnrolled, LOCALE));
        return switch (receipt) {
            case TotpEnrollmentMailReceipt.Sent _, TotpEnrollmentMailReceipt.AlreadySent _ -> Optional.empty();
            case TotpEnrollmentMailReceipt.Failed failed -> Optional.of(failed.reason());
        };
    }
}
