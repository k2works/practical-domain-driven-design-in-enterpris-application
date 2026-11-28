package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationTotpResetMails;
import com.example.cargotracker.identity.domain.events.TotpResetByAdmin;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 管理者による TOTP の登録の解除のお知らせのイベントハンドラー（Bolt 38d、US-18 AC9、SEC-10、ADR-017）。DE-26 を解除のコミットの後に、
 * 非同期で、新しいトランザクションの中で受け、利用者を読んで本人の登録メールアドレスへ解除のお知らせを送る（ADR-003）。停止中の利用者にも
 * 送る（リンクのない知らせで、悪用に本人が気づくため）。利用者がいなければ送らない。送信の失敗は例外にせず警告に残す（解除は戻さない）。
 * ログに出すのは利用者の ID と監査記録の ID だけ。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpResetMailEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TotpResetMailEventHandler.class);

    private final UserRepository users;
    private final NotificationTotpResetMails mails;

    public TotpResetMailEventHandler(UserRepository users, NotificationTotpResetMails mails) {
        this.users = users;
        this.mails = mails;
    }

    /** DE-26 を受けて、本人へ解除のお知らせを送る。 */
    @ApplicationModuleListener
    public void on(TotpResetByAdmin event) {
        Optional<User> user = users.findById(event.userId());
        if (user.isEmpty()) {
            LOG.warn(
                    "TOTP の解除のお知らせを送れない（利用者がいない）: 利用者 {}、監査記録 {}",
                    event.userId().value(),
                    event.auditRecordId());
            return;
        }
        mails.send(event.auditRecordId(), event.userId(), user.get().email(), event.resetAt())
                .ifPresent(reason -> LOG.warn(
                        "TOTP の解除のお知らせを送れなかった（解除はしている）: 利用者 {}、監査記録 {}、理由 {}",
                        event.userId().value(),
                        event.auditRecordId(),
                        reason));
    }
}
