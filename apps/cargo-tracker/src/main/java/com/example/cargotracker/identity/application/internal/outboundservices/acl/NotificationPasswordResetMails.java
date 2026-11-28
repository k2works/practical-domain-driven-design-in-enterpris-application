package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMail;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMailReceipt;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMailRequest;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知の再設定のメール。腐敗防止層で、通知の公開 API（{@link PasswordResetMail}）を呼び、結果の型をこの層の外に出さない（ADR-017。Bolt 33）。
 * 宛先は identity が渡す（通知は identity に依存しない）。
 */
public class NotificationPasswordResetMails {

    /** 文面の言語（今は日本語だけ。UI 設計）。 */
    static final String LOCALE = "ja";

    private final PasswordResetMail passwordResetMail;

    public NotificationPasswordResetMails(PasswordResetMail passwordResetMail) {
        this.passwordResetMail = passwordResetMail;
    }

    /**
     * password の再設定のメールを送る。依頼の ID と宛先で冪等。
     *
     * @param requestId 再設定の依頼の ID
     * @param tokenId 再設定のトークンの ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param resetUrl 再設定のリンク
     * @param expiresAt リンクの期限
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> send(
            UUID requestId, UUID tokenId, UserId userId, EmailAddress email, String resetUrl, UtcInstant expiresAt) {
        PasswordResetMailReceipt receipt = passwordResetMail.send(new PasswordResetMailRequest(
                requestId, tokenId, userId.value(), email.value(), resetUrl, expiresAt, LOCALE));
        return switch (receipt) {
            case PasswordResetMailReceipt.Sent _, PasswordResetMailReceipt.AlreadySent _ -> Optional.empty();
            case PasswordResetMailReceipt.Failed failed -> Optional.of(failed.reason());
        };
    }
}
