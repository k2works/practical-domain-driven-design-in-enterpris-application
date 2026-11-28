package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.notification.interfaces.api.TotpResetMail;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailRequest;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知の管理者による TOTP の登録の解除のお知らせ。腐敗防止層で、通知の公開 API（{@link TotpResetMail}）を呼び、結果の型をこの層の外に出さない
 * （ADR-017。Bolt 38d）。宛先は identity が渡す（通知は identity に依存しない）。
 */
public class NotificationTotpResetMails {

    /** 文面の言語（今は日本語だけ。UI 設計）。 */
    static final String LOCALE = "ja";

    private final TotpResetMail totpResetMail;

    public NotificationTotpResetMails(TotpResetMail totpResetMail) {
        this.totpResetMail = totpResetMail;
    }

    /**
     * 解除のお知らせを送る。監査記録の ID と宛先で冪等。
     *
     * @param auditRecordId 解除の監査記録の ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param resetAt 解除の時刻
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> send(UUID auditRecordId, UserId userId, EmailAddress email, UtcInstant resetAt) {
        TotpResetMailReceipt receipt = totpResetMail.send(
                new TotpResetMailRequest(auditRecordId, userId.value(), email.value(), resetAt, LOCALE));
        return switch (receipt) {
            case TotpResetMailReceipt.Sent _, TotpResetMailReceipt.AlreadySent _ -> Optional.empty();
            case TotpResetMailReceipt.Failed failed -> Optional.of(failed.reason());
        };
    }
}
