package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMail;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailReceipt;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailRequest;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知の利用者の招待のメール。腐敗防止層で、通知の公開 API（{@link UserInvitationMail}）を呼び、結果の型をこの層の外に出さない（ADR-017。
 * Bolt 38b2c）。宛先は identity が渡す（通知は identity に依存しない）。
 */
public class NotificationUserInvitationMails {

    /** 文面の言語（今は日本語だけ。UI 設計）。 */
    static final String LOCALE = "ja";

    private final UserInvitationMail userInvitationMail;

    public NotificationUserInvitationMails(UserInvitationMail userInvitationMail) {
        this.userInvitationMail = userInvitationMail;
    }

    /**
     * 利用者の招待のメールを送る。登録の ID と宛先で冪等。
     *
     * @param registrationId 登録の ID
     * @param tokenId 招待のトークンの ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param invitationUrl 初回の設定のリンク
     * @param expiresAt リンクの期限
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> send(
            UUID registrationId,
            UUID tokenId,
            UserId userId,
            EmailAddress email,
            String invitationUrl,
            UtcInstant expiresAt) {
        UserInvitationMailReceipt receipt = userInvitationMail.send(new UserInvitationMailRequest(
                registrationId, tokenId, userId.value(), email.value(), invitationUrl, expiresAt, LOCALE));
        return switch (receipt) {
            case UserInvitationMailReceipt.Sent _, UserInvitationMailReceipt.AlreadySent _ -> Optional.empty();
            case UserInvitationMailReceipt.Failed failed -> Optional.of(failed.reason());
        };
    }
}
