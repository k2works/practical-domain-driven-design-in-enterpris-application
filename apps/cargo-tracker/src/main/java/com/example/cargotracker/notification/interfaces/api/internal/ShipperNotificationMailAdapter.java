package com.example.cargotracker.notification.interfaces.api.internal;

import com.example.cargotracker.notification.application.internal.commands.SendShipperNotificationMailCommand;
import com.example.cargotracker.notification.application.internal.commandservices.MailDeliveryOutcome;
import com.example.cargotracker.notification.application.internal.commandservices.ShipperNotificationMailService;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMail;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMailReceipt;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMailRequest;
import org.springframework.stereotype.Component;

/**
 * 通知の公開 API（荷主への業務の通知）のインバウンドアダプター（ADR-017。US-22。Bolt 43）。送信のサービスに委ね、結果を公開 API の結果の型に変える。
 * 通知の型は公開 API の外に出さない。
 */
@Component
public class ShipperNotificationMailAdapter implements ShipperNotificationMail {

    private final ShipperNotificationMailService service;

    public ShipperNotificationMailAdapter(ShipperNotificationMailService service) {
        this.service = service;
    }

    @Override
    public ShipperNotificationMailReceipt send(ShipperNotificationMailRequest request) {
        MailDeliveryOutcome outcome = service.send(new SendShipperNotificationMailCommand(
                request.eventId(),
                request.recipientUserId(),
                request.email(),
                NotificationKind.valueOf(request.kind().name()),
                request.targetRef(),
                request.link(),
                request.locale()));
        return switch (outcome) {
            case MailDeliveryOutcome.Sent _ -> new ShipperNotificationMailReceipt.Sent();
            case MailDeliveryOutcome.AlreadySent _ -> new ShipperNotificationMailReceipt.AlreadySent();
            case MailDeliveryOutcome.Failed(String reason) -> new ShipperNotificationMailReceipt.Failed(reason);
        };
    }
}
