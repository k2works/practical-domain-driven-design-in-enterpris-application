package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationTotpEnrollmentMails;
import com.example.cargotracker.identity.domain.events.TotpEnrolled;
import com.example.cargotracker.identity.domain.events.TotpReEnrolled;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 認証アプリの登録と再登録のお知らせのイベントハンドラー（Bolt 38d、SEC-10、ADR-017）。DE-28 と DE-27 を登録・再登録のコミットの後に、非同期で、
 * 新しいトランザクションの中で受け、利用者を読んで本人の登録メールアドレスへお知らせを送る（ADR-003）。扱いは解除のお知らせ
 * （{@link TotpResetMailEventHandler}）と同じで、停止中の利用者にも送り、利用者がいなければ送らず、送信の失敗は例外にせず警告に残す。
 * ログに出すのは利用者の ID と監査記録の ID だけ。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpEnrollmentMailEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TotpEnrollmentMailEventHandler.class);

    private final UserRepository users;
    private final NotificationTotpEnrollmentMails mails;

    public TotpEnrollmentMailEventHandler(UserRepository users, NotificationTotpEnrollmentMails mails) {
        this.users = users;
        this.mails = mails;
    }

    /** DE-28 を受けて、本人へ登録のお知らせを送る。 */
    @ApplicationModuleListener
    public void on(TotpEnrolled event) {
        send(
                event.auditRecordId(),
                event.userId(),
                "登録",
                email -> mails.sendEnrolled(event.auditRecordId(), event.userId(), email, event.enrolledAt()));
    }

    /** DE-27 を受けて、本人へ再登録のお知らせを送る。 */
    @ApplicationModuleListener
    public void on(TotpReEnrolled event) {
        send(
                event.auditRecordId(),
                event.userId(),
                "再登録",
                email -> mails.sendReEnrolled(event.auditRecordId(), event.userId(), email, event.reEnrolledAt()));
    }

    /**
     * 利用者を読んでお知らせを送る。
     *
     * @param kind ログの文に入れる操作の名前（登録・再登録）
     * @param sending 宛先のメールアドレスを受けて送り、送れなかった理由を返す
     */
    private void send(
            UUID auditRecordId, UserId userId, String kind, Function<EmailAddress, Optional<String>> sending) {
        Optional<User> user = users.findById(userId);
        if (user.isEmpty()) {
            LOG.warn("認証アプリの{}のお知らせを送れない（利用者がいない）: 利用者 {}、監査記録 {}", kind, userId.value(), auditRecordId);
            return;
        }
        sending.apply(user.get().email())
                .ifPresent(reason -> LOG.warn(
                        "認証アプリの{}のお知らせを送れなかった（{}はしている）: 利用者 {}、監査記録 {}、理由 {}",
                        kind,
                        kind,
                        userId.value(),
                        auditRecordId,
                        reason));
    }
}
