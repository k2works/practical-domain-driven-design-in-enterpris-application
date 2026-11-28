package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationPasswordResetMails;
import com.example.cargotracker.identity.domain.events.PasswordResetRequested;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 再設定のメールのイベントハンドラー（Bolt 33、IA-INV-12、SEC-08、ADR-017）。DE-23 を依頼のコミットの後に、非同期で、新しい
 * トランザクションの中で受け、再設定のトークンを発行してメールを送る（ADR-003）。発行の手順（占有・再配信・1 分の抑え・有効か・前の未使用の
 * 無効化）は共有の部品 {@link PasswordResetTokenIssuance} が持つ（Bolt 38b2c）。送信の失敗は例外にせず完了にする（再配信で新しいトークンを
 * 作らない。利用者が依頼し直す）。リンクの値（平文）は発行の部品の返す値と送信の要求にだけあり、表・発行記録・ログに残らない。
 * SMTP の送信はこのトランザクションと利用者の行の占有の中で行い、送信は at-least-once になる（ADR-017。2026-10-15 の人の決定）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class PasswordResetMailEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetMailEventHandler.class);

    private final PasswordResetTokenIssuance issuance;
    private final NotificationPasswordResetMails mails;
    private final String resetLinkPrefix;

    /**
     * @param resetLinkPrefix リンクの接頭辞（公開の base URL と A-06 の path。リンクはこれにトークンを続ける。合成ルートが設定から確かめて
     *     渡す。request の Host ヘッダーから組み立てない）
     */
    @SuppressWarnings("java:S107") // 発行と送信に要るポートと設定をそろえて受ける
    public PasswordResetMailEventHandler(
            PasswordResetTokenRepository tokens,
            UserRepository users,
            CompanyRepository companies,
            PasswordResetTokenGenerator generator,
            PasswordResetTokenHasher hasher,
            NotificationPasswordResetMails mails,
            String resetLinkPrefix,
            Clock clock) {
        this.issuance = new PasswordResetTokenIssuance(tokens, users, companies, generator, hasher, clock);
        this.mails = mails;
        this.resetLinkPrefix = resetLinkPrefix;
    }

    /** DE-23 を受けてトークンを発行し、再設定のメールを送る。 */
    @ApplicationModuleListener
    public void on(PasswordResetRequested event) {
        issuance.issue(
                        PasswordResetTokenPurpose.RESET,
                        event.requestId(),
                        event.userId(),
                        event.requestedAt(),
                        resetLinkPrefix)
                .ifPresent(issued -> send(event, issued));
    }

    private void send(PasswordResetRequested event, PasswordResetTokenIssuance.IssuedLink issued) {
        mails.send(
                        event.requestId(),
                        issued.token().id(),
                        event.userId(),
                        issued.email(),
                        issued.link(),
                        issued.token().expiresAt())
                .ifPresent(reason -> LOG.warn(
                        "再設定のメールを送れなかった（利用者が依頼し直す）: 依頼 {}、トークン {}、理由 {}",
                        event.requestId(),
                        issued.token().id(),
                        reason));
    }
}
