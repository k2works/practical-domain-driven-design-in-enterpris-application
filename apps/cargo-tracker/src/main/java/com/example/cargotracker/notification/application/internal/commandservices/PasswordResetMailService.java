package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendPasswordResetMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.PasswordResetMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定のメールの送信のサービス（ADR-017。Bolt 33）。通知を送信待ちで記録してから送り、送信済みか失敗にする。同じ契機（依頼の ID）と
 * 宛先の通知がすでにあれば送らない（N-INV-02）。送信の失敗は例外にせず失敗として記録して返す（再配信で新しいトークンを作らないため。
 * 利用者が依頼し直す）。本文とリンクは記録しない（N-INV-01）。呼ぶ側にトランザクションがあれば加わる（identity のイベントハンドラーの
 * トランザクションで、トークンの発行と通知の記録を一緒に確定する）。
 */
@Service
public class PasswordResetMailService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetMailService.class);

    /** 失敗の理由でリンクの代わりに置く文字列。 */
    static final String LINK_PLACEHOLDER = "[再設定のリンク]";

    private final NotificationRepository notifications;
    private final MailSender sender;
    private final PasswordResetMailTemplate template;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PasswordResetMailService(
            NotificationRepository notifications,
            MailSender sender,
            PasswordResetMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.notifications = notifications;
        this.sender = sender;
        this.template = template;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /** password の再設定のメールを送り、結果を記録する。 */
    public PasswordResetMailOutcome send(SendPasswordResetMailCommand command) {
        Notification pending = Notification.pending(
                command.requestId(),
                command.recipientUserId(),
                command.email(),
                NotificationKind.PASSWORD_RESET,
                command.tokenId().toString(),
                command.locale(),
                now());
        Boolean added = transactionTemplate.execute(status -> notifications.addIfAbsent(pending));
        if (!Boolean.TRUE.equals(added)) {
            return new PasswordResetMailOutcome.AlreadySent();
        }
        try {
            OutgoingMail mail =
                    template.compose(command.email(), command.resetUrl(), command.expiresAt(), command.locale());
            sender.send(mail);
        } catch (RuntimeException e) {
            String reason = reasonOf(e, command.resetUrl());
            LOG.warn("password の再設定のメールを送れなかった: 通知 {}、理由 {}", pending.id(), reason);
            record(pending.failed(reason));
            return new PasswordResetMailOutcome.Failed(reason);
        }
        record(pending.sent(now()));
        return new PasswordResetMailOutcome.Sent();
    }

    private void record(Notification outcome) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!notifications.recordOutcome(outcome)) {
                throw new IllegalStateException("送信待ちでない通知に送信の結果を書こうとした: " + outcome.id());
            }
        });
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }

    /** 例外の型と文。リンクが入っていたら置き換える（記録とログにリンクを残さない）。 */
    private static String reasonOf(RuntimeException e, String resetUrl) {
        String message = String.valueOf(e.getMessage()).replace(resetUrl, LINK_PLACEHOLDER);
        return e.getClass().getSimpleName() + ": " + message;
    }
}
