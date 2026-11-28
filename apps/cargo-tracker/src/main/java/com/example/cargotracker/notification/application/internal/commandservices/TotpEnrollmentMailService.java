package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendTotpEnrollmentMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.TotpEnrollmentMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 認証アプリの登録と再登録のお知らせの送信のサービス（ADR-017。Bolt 38d）。記録と送信と伏せ字は {@link MailDelivery} と共有する（リンクを
 * 持たないので、伏せるのはメールアドレスだけ）。種類はコマンドの {@code reEnrolled} で選ぶ。
 */
@Service
public class TotpEnrollmentMailService {

    private final MailDelivery delivery;
    private final TotpEnrollmentMailTemplate template;

    public TotpEnrollmentMailService(
            NotificationRepository notifications,
            MailSender sender,
            TotpEnrollmentMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.delivery = new MailDelivery(notifications, sender, clock, transactionTemplate);
        this.template = template;
    }

    /** 登録か再登録のお知らせを送り、結果を記録する（種類は {@code TOTP_ENROLLED} か {@code TOTP_RE_ENROLLED}、契機と対象は監査記録の ID）。 */
    public MailDeliveryOutcome send(SendTotpEnrollmentMailCommand command) {
        Notification pending = Notification.pending(
                command.auditRecordId(),
                command.recipientUserId(),
                command.email(),
                command.reEnrolled() ? NotificationKind.TOTP_RE_ENROLLED : NotificationKind.TOTP_ENROLLED,
                command.auditRecordId().toString(),
                command.locale(),
                delivery.now());
        return delivery.deliver(
                pending,
                () -> template.compose(command.email(), command.enrolledAt(), command.reEnrolled(), command.locale()),
                List.of());
    }
}
