package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationPasswordResetMails;
import com.example.cargotracker.identity.domain.events.PasswordResetRequested;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 再設定のメールのイベントハンドラー（Bolt 33、IA-INV-12、SEC-08、ADR-017）。DE-23 を依頼のコミットの後に、非同期で、新しい
 * トランザクションの中で受け、再設定のトークンを発行してメールを送る（ADR-003）。発行は利用者の行の占有で直列にする（同時の 2 つの依頼で
 * 2 通送らない）。同じ依頼の ID のトークンがあれば何もしない（再配信で 2 通送らない）。前の発行から 1 分たっていなければ発行も送信も
 * しない。発行するときは前の未使用のトークンを無効にする。送信の失敗は例外にせず完了にする（再配信で新しいトークンを作らない。利用者が
 * 依頼し直す）。リンクの値（平文）はこのメソッドの中と送信の要求にだけあり、表・発行記録・ログに残らない。
 * SMTP の送信はこのトランザクションと利用者の行の占有の中で行い、送信は at-least-once になる（ADR-017。2026-10-15 の人の決定）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class PasswordResetMailEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetMailEventHandler.class);

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordResetTokenGenerator generator;
    private final PasswordResetTokenHasher hasher;
    private final NotificationPasswordResetMails mails;
    private final String resetLinkPrefix;
    private final Clock clock;

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
        this.tokens = tokens;
        this.users = users;
        this.companies = companies;
        this.generator = generator;
        this.hasher = hasher;
        this.mails = mails;
        this.resetLinkPrefix = resetLinkPrefix;
        this.clock = clock;
    }

    /** DE-23 を受けてトークンを発行し、再設定のメールを送る。 */
    @ApplicationModuleListener
    public void on(PasswordResetRequested event) {
        UserId userId = event.userId();
        if (!tokens.lockForIssue(userId)) {
            LOG.info("再設定のトークンを発行しなかった（利用者がいない）: 依頼 {}", event.requestId());
            return;
        }
        if (tokens.existsByRequestId(event.requestId())) {
            LOG.info("再設定のトークンを発行しなかった（同じ依頼の再配信）: 依頼 {}", event.requestId());
            return;
        }
        UtcInstant now = new UtcInstant(clock.instant());
        if (!PasswordResetToken.allowsIssue(tokens.findLastIssuedAt(userId, PasswordResetTokenPurpose.RESET), now)) {
            LOG.info("再設定のトークンを発行しなかった（前の発行から 1 分たっていない）: 依頼 {}", event.requestId());
            return;
        }
        Optional<User> user = users.findById(userId).filter(this::isActive);
        if (user.isEmpty()) {
            LOG.info("再設定のトークンを発行しなかった（依頼の後に利用者が無効になった）: 依頼 {}", event.requestId());
            return;
        }
        tokens.invalidateUnused(userId, now);
        String token = generator.generate();
        PasswordResetToken issued = PasswordResetToken.issue(
                PasswordResetTokenPurpose.RESET, event.requestId(), userId, hasher.hash(token), now);
        tokens.add(issued);
        mails.send(
                        event.requestId(),
                        issued.id(),
                        userId,
                        user.get().email(),
                        resetLinkPrefix + token,
                        issued.expiresAt())
                .ifPresent(reason -> LOG.warn(
                        "再設定のメールを送れなかった（利用者が依頼し直す）: 依頼 {}、トークン {}、理由 {}", event.requestId(), issued.id(), reason));
    }

    private boolean isActive(User user) {
        Optional<Company> company = companies.findById(user.companyId());
        return company.isPresent()
                && user.authenticationRejection(company.get()).isEmpty();
    }
}
