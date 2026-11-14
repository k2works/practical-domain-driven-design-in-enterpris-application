package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendShipperNotificationMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.ShipperNotificationMailTemplate;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 荷主への業務の通知の送信のサービス（ADR-017。US-22。Bolt 43）。記録と送信と伏せ字はほかのメールと共有する（{@link MailDelivery}）。画面への導線は
 * 秘密ではないので伏せない（伏せるのはメールアドレスだけ）。同じ契機と宛先の通知がすでにあれば送らない（N-INV-02）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code NotificationConfiguration} が担う。
 */
@Service
public class ShipperNotificationMailService {

    private final MailDelivery delivery;
    private final ShipperNotificationMailTemplate template;

    public ShipperNotificationMailService(
            NotificationRepository notifications,
            MailSender sender,
            ShipperNotificationMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.delivery = new MailDelivery(notifications, sender, clock, transactionTemplate);
        this.template = template;
    }

    /** 通知を送り、結果を記録する。 */
    public MailDeliveryOutcome send(SendShipperNotificationMailCommand command) {
        Notification pending = Notification.pending(
                command.eventId(),
                command.recipientUserId(),
                command.email(),
                command.kind(),
                command.targetRef(),
                command.locale(),
                delivery.now());
        return delivery.deliver(
                pending,
                () -> template.compose(
                        command.email(), command.kind(), command.targetRef(), command.link(), command.locale()),
                List.of());
    }
}
