package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * リンクのあるメールの送り方（Bolt 38b2b。再設定のメール（Bolt 33）から、利用者の招待のメールと共有するために抜き出した）。通知を送信待ちで
 * 記録し、文面を組み立てて送り、送信済みか失敗にする。同じ契機と宛先の通知がすでにあれば送らない（N-INV-02）。文面の組み立てと送信の失敗は
 * 例外にせず失敗として記録して返す（再配信で新しいトークンを作らないため）。本文とリンクは記録しない（N-INV-01）。失敗の理由とログには、
 * リンクとその最後の段（トークン）とメールアドレスを残さない。Bean にせず、各サービスがコンストラクターの引数から作る（前例
 * {@code TotpProvisioningStarter}）。
 *
 * <p>呼ぶ側にトランザクションがあれば加わる（identity のイベントハンドラーから呼ばれるときは、トークンの発行・通知の記録・送信の結果が
 * 1 つのトランザクションで確定し、送信は at-least-once になる。ADR-017 の決定 4）。
 */
final class LinkMailDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(LinkMailDelivery.class);

    /** 失敗の理由でリンクの代わりに置く文字列。 */
    private static final String LINK_PLACEHOLDER = "[リンク]";

    /** 失敗の理由でメールアドレスの代わりに置く文字列。 */
    private static final String ADDRESS_PLACEHOLDER = "[メールアドレス]";

    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[^\\s<>\"'(),;:]+@[^\\s<>\"'(),;:]+");

    private final NotificationRepository notifications;
    private final MailSender sender;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    LinkMailDelivery(
            NotificationRepository notifications,
            MailSender sender,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 送信待ちの通知を記録し、メールを組み立てて送り、結果を記録する。
     *
     * @param pending 送信待ちの通知（種類・契機・宛先・target_ref を各サービスが決める）
     * @param link 伏せるリンク
     * @param mail 送るメールの組み立て（記録の後に、送信と同じ try の中で呼ぶ）
     */
    MailDeliveryOutcome deliver(Notification pending, String link, Supplier<OutgoingMail> mail) {
        Boolean added = transactionTemplate.execute(status -> notifications.addIfAbsent(pending));
        if (!Boolean.TRUE.equals(added)) {
            return new MailDeliveryOutcome.AlreadySent();
        }
        try {
            sender.send(mail.get());
        } catch (RuntimeException e) {
            String reason = reasonOf(e, link);
            LOG.warn("{} のメールを送れなかった: 契機 {}、通知 {}、理由 {}", pending.kind(), pending.eventId(), pending.id(), reason);
            saveOutcome(pending.failed(reason));
            return new MailDeliveryOutcome.Failed(reason);
        }
        saveOutcome(pending.sent(now()));
        return new MailDeliveryOutcome.Sent();
    }

    /** 今の時刻（各サービスが送信待ちの通知を作るときにも使う）。 */
    UtcInstant now() {
        return new UtcInstant(clock.instant());
    }

    /**
     * 送信の結果を書く。送った後なので、書けなくても例外にしない（例外にすると呼ぶ側のトランザクションが戻り、届いたリンクのトークンが
     * 消える）。同じ通知を同時に 2 つが書くことはない（記録は契機と宛先で 1 つ）ので、書けないのは想定の外で、警告を残す。
     */
    private void saveOutcome(Notification outcome) {
        Boolean saved = transactionTemplate.execute(status -> notifications.recordOutcome(outcome));
        if (!Boolean.TRUE.equals(saved)) {
            LOG.warn("送信待ちでない通知に送信の結果を書こうとした: 通知 {}、結果 {}", outcome.id(), outcome.status());
        }
    }

    /**
     * 例外の型と文。リンク（とその最後の段のトークン）とメールアドレスを伏せる（記録とログに残さない。SMTP の応答の文には宛先が入ることがある）。
     */
    private static String reasonOf(RuntimeException e, String link) {
        String message = String.valueOf(e.getMessage()).replace(link, LINK_PLACEHOLDER);
        String token = link.substring(link.lastIndexOf('/') + 1);
        if (!token.isEmpty()) {
            message = message.replace(token, LINK_PLACEHOLDER);
        }
        message = EMAIL_ADDRESS.matcher(message).replaceAll(ADDRESS_PLACEHOLDER);
        return e.getClass().getSimpleName() + ": " + message;
    }
}
