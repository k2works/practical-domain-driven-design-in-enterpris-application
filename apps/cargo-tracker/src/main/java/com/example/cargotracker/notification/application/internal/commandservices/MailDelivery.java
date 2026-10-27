package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * メールの送り方（Bolt 38b2b。再設定のメール（Bolt 33）から、利用者の招待のメールと共有するために抜き出した。Bolt 38d でリンクのない
 * メール（TOTP の解除・登録・再登録のお知らせ）にも使える形に広げ、名前を {@code LinkMailDelivery} から変えた）。通知を送信待ちで記録し、
 * 文面を組み立てて送り、送信済みか失敗にする。同じ契機と宛先の通知がすでにあれば送らない（N-INV-02）。文面の組み立てと送信の失敗は例外に
 * せず失敗として記録して返す（メールの失敗で、呼ぶ側の契機の処理（トークンの発行、解除・登録・再登録）を戻さないため）。本文とリンクは記録
 * しない（N-INV-01）。失敗の理由とログには、呼ぶ側が渡した伏せる文字列（リンクとその最後の段（トークン））と、メールアドレスの形の文字列を
 * 残さない（メールアドレスは形で伏せるので、呼ぶ側は渡さない）。Bean にせず、各サービスがコンストラクターの引数から作る（前例
 * {@code TotpProvisioningStarter}）。
 *
 * <p>呼ぶ側にトランザクションがあれば加わる（identity のイベントハンドラーから呼ばれるときは、トークンの発行・通知の記録・送信の結果が
 * 1 つのトランザクションで確定し、送信は at-least-once になる。ADR-017 の決定 4）。
 */
final class MailDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(MailDelivery.class);

    /** 失敗の理由でリンクの代わりに置く文字列。 */
    private static final String LINK_PLACEHOLDER = "[リンク]";

    /** 失敗の理由でメールアドレスの代わりに置く文字列。 */
    private static final String ADDRESS_PLACEHOLDER = "[メールアドレス]";

    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[^\\s<>\"'(),;:]+@[^\\s<>\"'(),;:]+");

    private final NotificationRepository notifications;
    private final MailSender sender;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    MailDelivery(
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
     * 失敗の理由とログから伏せる文字列。
     *
     * @param text 伏せる文字列（空は拒む。空を伏せると理由の文の 1 字ごとに置き換えの文字列が入る）
     * @param placeholder 代わりに置く文字列
     */
    record Mask(String text, String placeholder) {

        Mask {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(placeholder, "placeholder");
            if (text.isEmpty()) {
                throw new IllegalArgumentException("伏せる文字列が空");
            }
        }
    }

    /** リンクのあるメールの伏せる文字列（リンクとその最後の段（トークン）。再設定と招待）。 */
    static List<Mask> linkMasks(String link) {
        String token = link.substring(link.lastIndexOf('/') + 1);
        List<Mask> masks = new ArrayList<>();
        masks.add(new Mask(link, LINK_PLACEHOLDER));
        if (!token.isEmpty()) {
            masks.add(new Mask(token, LINK_PLACEHOLDER));
        }
        return List.copyOf(masks);
    }

    /**
     * 送信待ちの通知を記録し、メールを組み立てて送り、結果を記録する。
     *
     * @param pending 送信待ちの通知（種類・契機・宛先・target_ref を各サービスが決める）
     * @param mail 送るメールの組み立て（記録の後に、送信と同じ try の中で呼ぶ）
     * @param masks 失敗の理由とログから伏せる文字列（リンクのあるメールは {@link #linkMasks}、リンクのないメールは空）
     */
    MailDeliveryOutcome deliver(Notification pending, Supplier<OutgoingMail> mail, List<Mask> masks) {
        Boolean added = transactionTemplate.execute(status -> notifications.addIfAbsent(pending));
        if (!Boolean.TRUE.equals(added)) {
            return new MailDeliveryOutcome.AlreadySent();
        }
        try {
            sender.send(mail.get());
        } catch (RuntimeException e) {
            String reason = reasonOf(e, masks);
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
     * 送信の結果を書く。送った後なので、書けなくても例外にしない（例外にすると呼ぶ側のトランザクションが戻り、届いたメールの契機
     * （リンクのトークン、解除・登録・再登録の通知の記録）が消える）。同じ通知を同時に 2 つが書くことはない（記録は契機と宛先で 1 つ）ので、書けないのは想定の外で、警告を残す。
     */
    private void saveOutcome(Notification outcome) {
        Boolean saved = transactionTemplate.execute(status -> notifications.recordOutcome(outcome));
        if (!Boolean.TRUE.equals(saved)) {
            LOG.warn("送信待ちでない通知に送信の結果を書こうとした: 通知 {}、結果 {}", outcome.id(), outcome.status());
        }
    }

    /**
     * 例外の型と文。伏せる文字列とメールアドレスの形の文字列を伏せる（記録とログに残さない。SMTP の応答の文には宛先が入ることがある）。
     */
    private static String reasonOf(RuntimeException e, List<Mask> masks) {
        String message = String.valueOf(e.getMessage());
        for (Mask mask : masks) {
            message = message.replace(mask.text(), mask.placeholder());
        }
        message = EMAIL_ADDRESS.matcher(message).replaceAll(ADDRESS_PLACEHOLDER);
        return e.getClass().getSimpleName() + ": " + message;
    }
}
