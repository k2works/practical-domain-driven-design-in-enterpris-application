package com.example.cargotracker.notification.interfaces.api.internal;

import com.example.cargotracker.notification.application.internal.commands.SendTotpEnrollmentMailCommand;
import com.example.cargotracker.notification.application.internal.commandservices.MailDeliveryOutcome;
import com.example.cargotracker.notification.application.internal.commandservices.TotpEnrollmentMailService;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMail;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailRequest;
import org.springframework.stereotype.Component;

/**
 * 通知の公開 API（認証アプリの登録と再登録のお知らせ）のインバウンドアダプター（ADR-017。Bolt 38d）。送信のサービスに委ね、結果を公開 API の
 * 結果の型に変える。通知の型は公開 API の外に出さない。
 */
@Component
public class TotpEnrollmentMailAdapter implements TotpEnrollmentMail {

    private final TotpEnrollmentMailService service;

    public TotpEnrollmentMailAdapter(TotpEnrollmentMailService service) {
        this.service = service;
    }

    @Override
    public TotpEnrollmentMailReceipt send(TotpEnrollmentMailRequest request) {
        MailDeliveryOutcome outcome = service.send(new SendTotpEnrollmentMailCommand(
                request.auditRecordId(),
                request.recipientUserId(),
                request.email(),
                request.enrolledAt(),
                request.reEnrolled(),
                request.locale()));
        return switch (outcome) {
            case MailDeliveryOutcome.Sent _ -> new TotpEnrollmentMailReceipt.Sent();
            case MailDeliveryOutcome.AlreadySent _ -> new TotpEnrollmentMailReceipt.AlreadySent();
            case MailDeliveryOutcome.Failed(String reason) -> new TotpEnrollmentMailReceipt.Failed(reason);
        };
    }
}
