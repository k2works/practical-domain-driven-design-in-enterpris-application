package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationUserInvitationMails;
import com.example.cargotracker.identity.domain.events.UserRegistered;
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
 * 利用者の招待のメールのイベントハンドラー（Bolt 38b2c、US-16 AC1、IA-INV-12、SEC-08、ADR-017）。DE-25 を登録のコミットの後に、非同期で、
 * 新しいトランザクションの中で受け、招待のトークン（{@code USER_INVITATION}、24 時間）を発行して招待のメールを送る（ADR-003）。発行の手順
 * （占有・再配信・有効か・前の未使用の無効化）は共有の部品 {@link PasswordResetTokenIssuance} が持つ（招待は繰り返しの抑えを数えない）。
 * 登録の後に利用者が止められていれば発行しない。送信の失敗は例外にせず完了にする（トークンは残る。本人が A-05 で設定する）。招待の用途を
 * 参照してよいのはこのクラスだけ（ArchUnit の規則）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class UserInvitationMailEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserInvitationMailEventHandler.class);

    private final PasswordResetTokenIssuance issuance;
    private final NotificationUserInvitationMails mails;
    private final String invitationLinkPrefix;

    /**
     * @param invitationLinkPrefix リンクの接頭辞（公開の base URL と A-06 の path。リンクはこれにトークンを続ける）
     */
    @SuppressWarnings("java:S107") // 発行と送信に要るポートと設定をそろえて受ける
    public UserInvitationMailEventHandler(
            PasswordResetTokenRepository tokens,
            UserRepository users,
            CompanyRepository companies,
            PasswordResetTokenGenerator generator,
            PasswordResetTokenHasher hasher,
            NotificationUserInvitationMails mails,
            String invitationLinkPrefix,
            Clock clock) {
        this.issuance = new PasswordResetTokenIssuance(tokens, users, companies, generator, hasher, clock);
        this.mails = mails;
        this.invitationLinkPrefix = invitationLinkPrefix;
    }

    /** DE-25 を受けて招待のトークンを発行し、招待のメールを送る。 */
    @ApplicationModuleListener
    public void on(UserRegistered event) {
        issuance.issue(
                        PasswordResetTokenPurpose.USER_INVITATION,
                        event.registrationId(),
                        event.userId(),
                        invitationLinkPrefix)
                .ifPresent(issued -> send(event, issued));
    }

    private void send(UserRegistered event, PasswordResetTokenIssuance.IssuedLink issued) {
        mails.send(
                        event.registrationId(),
                        issued.token().id(),
                        event.userId(),
                        issued.email(),
                        issued.link(),
                        issued.token().expiresAt())
                .ifPresent(reason -> LOG.warn(
                        "利用者の招待のメールを送れなかった（本人が A-05 で設定する）: 登録 {}、トークン {}、理由 {}",
                        event.registrationId(),
                        issued.token().id(),
                        reason));
    }
}
