package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendUserInvitationMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.UserInvitationMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の招待のメールの送信のサービス（ADR-017。Bolt 38b2b）。記録と送信と伏せ字は再設定のメールと共有する（{@link MailDelivery}）。
 */
@Service
public class UserInvitationMailService {

    private final MailDelivery delivery;
    private final UserInvitationMailTemplate template;

    public UserInvitationMailService(
            NotificationRepository notifications,
            MailSender sender,
            UserInvitationMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.delivery = new MailDelivery(notifications, sender, clock, transactionTemplate);
        this.template = template;
    }

    /** 利用者の招待のメールを送り、結果を記録する（種類は {@code USER_INVITATION}、対象はトークンの ID）。 */
    public MailDeliveryOutcome send(SendUserInvitationMailCommand command) {
        Notification pending = Notification.pending(
                command.registrationId(),
                command.recipientUserId(),
                command.email(),
                NotificationKind.USER_INVITATION,
                command.tokenId().toString(),
                command.locale(),
                delivery.now());
        return delivery.deliver(
                pending,
                () -> template.compose(command.email(), command.invitationUrl(), command.expiresAt(), command.locale()),
                MailDelivery.linkMasks(command.invitationUrl()));
    }
}
