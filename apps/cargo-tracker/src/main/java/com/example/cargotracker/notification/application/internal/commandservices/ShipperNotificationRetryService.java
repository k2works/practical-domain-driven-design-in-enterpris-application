package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.ShipperNotificationMailTemplate;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 一時の理由で失敗した荷主への業務の通知を送り直す（ADR-021 の C-3。W10-1 の Bolt 61）。通知を 1 件ずつ引き取ってから送る。
 * 宛先は失敗したときに記録したメールアドレスで、利用者の今の状態は確かめない（通知は identity に依存しない）。そのため再送は作成から
 * {@link Notification#RETRY_WINDOW} の中に限る。引き取った後にアプリが止まると、通知は送信待ちのまま残る（最初の送信と同じ）。
 */
@Service
public class ShipperNotificationRetryService {

    /** 1 回の実行で扱う件数の上限。残りは次の実行で扱う。 */
    private static final int BATCH_SIZE = 100;

    private final NotificationRepository notifications;
    private final ShipperNotificationMailTemplate template;
    private final MailDelivery delivery;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ShipperNotificationRetryService(
            NotificationRepository notifications,
            MailSender sender,
            ShipperNotificationMailTemplate template,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.notifications = notifications;
        this.template = template;
        this.delivery = new MailDelivery(notifications, sender, clock, transactionTemplate);
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @return 送り直しを試みた件数
     */
    public int retryDue() {
        UtcInstant now = new UtcInstant(clock.instant());
        List<Notification> candidates = notifications.findRetryCandidates(
                new UtcInstant(now.instant().minus(Notification.RETRY_WINDOW)), BATCH_SIZE);
        int attempted = 0;
        for (Notification candidate : candidates) {
            Optional<Notification> retrying = candidate.retry(now).filter(this::claim);
            if (retrying.isPresent()) {
                attempted++;
                if (!send(retrying.get())) {
                    break;
                }
            }
        }
        return attempted;
    }

    private boolean claim(Notification retrying) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> notifications.claimRetry(retrying)));
    }

    /**
     * @return 次の通知も続けて送ってよいか。一時の失敗は送り先の不調とみなして打ち切る（残りは引き取らず、再送の回数を使わない）
     */
    private boolean send(Notification retrying) {
        String link = retrying.link().orElseThrow();
        return !delivery.attemptEndedInTransientFailure(
                retrying,
                () -> template.compose(
                        retrying.email(), retrying.kind(), retrying.targetRef(), link, retrying.locale()),
                List.of());
    }
}
