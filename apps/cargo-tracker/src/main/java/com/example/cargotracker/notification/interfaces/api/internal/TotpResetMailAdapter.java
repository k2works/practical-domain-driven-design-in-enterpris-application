package com.example.cargotracker.notification.interfaces.api.internal;

import com.example.cargotracker.notification.application.internal.commands.SendTotpResetMailCommand;
import com.example.cargotracker.notification.application.internal.commandservices.MailDeliveryOutcome;
import com.example.cargotracker.notification.application.internal.commandservices.TotpResetMailService;
import com.example.cargotracker.notification.interfaces.api.TotpResetMail;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailRequest;
import org.springframework.stereotype.Component;

/**
 * 通知の公開 API（管理者による TOTP の登録の解除のお知らせ）のインバウンドアダプター（ADR-017。Bolt 38d）。送信のサービスに委ね、結果を
 * 公開 API の結果の型に変える。通知の型は公開 API の外に出さない。
 */
@Component
public class TotpResetMailAdapter implements TotpResetMail {

    private final TotpResetMailService service;

    public TotpResetMailAdapter(TotpResetMailService service) {
        this.service = service;
    }

    @Override
    public TotpResetMailReceipt send(TotpResetMailRequest request) {
        MailDeliveryOutcome outcome = service.send(new SendTotpResetMailCommand(
                request.auditRecordId(),
                request.recipientUserId(),
                request.email(),
                request.resetAt(),
                request.locale()));
        return switch (outcome) {
            case MailDeliveryOutcome.Sent _ -> new TotpResetMailReceipt.Sent();
            case MailDeliveryOutcome.AlreadySent _ -> new TotpResetMailReceipt.AlreadySent();
            case MailDeliveryOutcome.Failed(String reason) -> new TotpResetMailReceipt.Failed(reason);
        };
    }
}
