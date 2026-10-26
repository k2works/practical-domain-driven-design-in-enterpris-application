package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.application.internal.commands.SendPasswordResetMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.PasswordResetMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定のメールの送信のサービス（ADR-017。Bolt 33）。通知を送信待ちで記録し、送り、送信済みか失敗にする。同じ契機（依頼の ID）と
 * 宛先の通知がすでにあれば送らない（N-INV-02）。送信の失敗は例外にせず失敗として記録して返す（再配信で新しいトークンを作らないため。
 * 利用者が依頼し直す）。本文とリンクは記録しない（N-INV-01）。失敗の理由とログには、リンクのトークンとメールアドレスを残さない。
 *
 * <p>呼ぶ側にトランザクションがあれば加わる。identity のイベントハンドラーから呼ばれるときは、トークンの発行・通知の記録・送信の結果が
 * 1 つのトランザクションで確定し、送信は at-least-once になる（送った後、コミットの前に落ちると、届いたリンクは使えず、再配信で新しい
 * リンクの 2 通目が送られることがある。2026-10-15 の人の決定。SMTP は 5 秒で打ち切る）。
 */
@Service
public class PasswordResetMailService {

    private final LinkMailDelivery delivery;
    private final PasswordResetMailTemplate template;

    public PasswordResetMailService(
            NotificationRepository notifications,
            MailSender sender,
            PasswordResetMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.delivery = new LinkMailDelivery(notifications, sender, clock, transactionTemplate);
        this.template = template;
    }

    /** password の再設定のメールを送り、結果を記録する（記録と送信と伏せ字は {@link LinkMailDelivery}。Bolt 38b2b）。 */
    public MailDeliveryOutcome send(SendPasswordResetMailCommand command) {
        Notification pending = Notification.pending(
                command.requestId(),
                command.recipientUserId(),
                command.email(),
                NotificationKind.PASSWORD_RESET,
                command.tokenId().toString(),
                command.locale(),
                delivery.now());
        return delivery.deliver(
                pending,
                command.resetUrl(),
                () -> template.compose(command.email(), command.resetUrl(), command.expiresAt(), command.locale()));
    }
}
