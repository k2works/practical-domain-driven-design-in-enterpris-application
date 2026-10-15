package com.example.cargotracker.notification.interfaces.api.internal;

import com.example.cargotracker.notification.application.internal.commands.SendPasswordResetMailCommand;
import com.example.cargotracker.notification.application.internal.commandservices.PasswordResetMailOutcome;
import com.example.cargotracker.notification.application.internal.commandservices.PasswordResetMailService;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMail;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMailReceipt;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMailRequest;
import org.springframework.stereotype.Component;

/**
 * 通知の公開 API（再設定のメール）のインバウンドアダプター（ADR-017。Bolt 33）。送信のサービスに委ね、結果を公開 API の結果の型に変える。
 * 通知の型は公開 API の外に出さない。
 */
@Component
public class PasswordResetMailAdapter implements PasswordResetMail {

    private final PasswordResetMailService service;

    public PasswordResetMailAdapter(PasswordResetMailService service) {
        this.service = service;
    }

    @Override
    public PasswordResetMailReceipt send(PasswordResetMailRequest request) {
        PasswordResetMailOutcome outcome = service.send(new SendPasswordResetMailCommand(
                request.requestId(),
                request.tokenId(),
                request.recipientUserId(),
                request.email(),
                request.resetUrl(),
                request.expiresAt(),
                request.locale()));
        return switch (outcome) {
            case PasswordResetMailOutcome.Sent _ -> new PasswordResetMailReceipt.Sent();
            case PasswordResetMailOutcome.AlreadySent _ -> new PasswordResetMailReceipt.AlreadySent();
            case PasswordResetMailOutcome.Failed failed -> new PasswordResetMailReceipt.Failed(failed.reason());
        };
    }
}
