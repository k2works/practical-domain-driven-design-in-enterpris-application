package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendTotpResetMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.TotpResetMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 管理者による TOTP の登録の解除のお知らせの送信のサービス（ADR-017。Bolt 38d）。記録と送信と伏せ字は再設定と招待のメールと共有する
 * （{@link MailDelivery}。リンクを持たないので、伏せるのはメールアドレスだけ）。
 */
@Service
public class TotpResetMailService {

    private final MailDelivery delivery;
    private final TotpResetMailTemplate template;

    public TotpResetMailService(
            NotificationRepository notifications,
            MailSender sender,
            TotpResetMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.delivery = new MailDelivery(notifications, sender, clock, transactionTemplate);
        this.template = template;
    }

    /** 解除のお知らせを送り、結果を記録する（種類は {@code TOTP_RESET_BY_ADMIN}、契機と対象は監査記録の ID）。 */
    public MailDeliveryOutcome send(SendTotpResetMailCommand command) {
        Notification pending = Notification.pending(
                command.auditRecordId(),
                command.recipientUserId(),
                command.email(),
                NotificationKind.TOTP_RESET_BY_ADMIN,
                command.auditRecordId().toString(),
                command.locale(),
                delivery.now());
        return delivery.deliver(
                pending, () -> template.compose(command.email(), command.resetAt(), command.locale()), List.of());
    }
}
