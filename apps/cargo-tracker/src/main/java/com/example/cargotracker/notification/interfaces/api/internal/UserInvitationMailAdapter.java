package com.example.cargotracker.notification.interfaces.api.internal;

import com.example.cargotracker.notification.application.internal.commands.SendUserInvitationMailCommand;
import com.example.cargotracker.notification.application.internal.commandservices.MailDeliveryOutcome;
import com.example.cargotracker.notification.application.internal.commandservices.UserInvitationMailService;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMail;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailReceipt;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailRequest;
import org.springframework.stereotype.Component;

/**
 * 通知の公開 API（利用者の招待のメール）のインバウンドアダプター（ADR-017。Bolt 38b2b）。送信のサービスに委ね、結果を公開 API の結果の型に
 * 変える。通知の型は公開 API の外に出さない。
 */
@Component
public class UserInvitationMailAdapter implements UserInvitationMail {

    private final UserInvitationMailService service;

    public UserInvitationMailAdapter(UserInvitationMailService service) {
        this.service = service;
    }

    @Override
    public UserInvitationMailReceipt send(UserInvitationMailRequest request) {
        MailDeliveryOutcome outcome = service.send(new SendUserInvitationMailCommand(
                request.registrationId(),
                request.tokenId(),
                request.recipientUserId(),
                request.email(),
                request.invitationUrl(),
                request.expiresAt(),
                request.locale()));
        return switch (outcome) {
            case MailDeliveryOutcome.Sent _ -> new UserInvitationMailReceipt.Sent();
            case MailDeliveryOutcome.AlreadySent _ -> new UserInvitationMailReceipt.AlreadySent();
            case MailDeliveryOutcome.Failed(String reason) -> new UserInvitationMailReceipt.Failed(reason);
        };
    }
}
