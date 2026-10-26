package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.UserInvitationMailTemplate;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 利用者の招待のメールの文面（UI 設計「利用者の招待のメール」。Bolt 38b2b）。Thymeleaf のテキストのテンプレート
 * （{@code classpath:mail/user-invitation.txt}）で本文を組み立てる（エンジンと日時の書式は {@link MailTextTemplates}）。
 */
public final class ThymeleafUserInvitationMailTemplate implements UserInvitationMailTemplate {

    /** 件名（UI 設計）。 */
    static final String SUBJECT = "【cargo-tracker】利用者の登録のご案内";

    private final SpringTemplateEngine engine = MailTextTemplates.engine();

    @Override
    public OutgoingMail compose(String to, String invitationUrl, UtcInstant expiresAt, String locale) {
        Context context = new Context(Locale.forLanguageTag(locale));
        context.setVariable("invitationUrl", invitationUrl);
        context.setVariable("expiresAt", MailTextTemplates.dateTime(expiresAt));
        return new OutgoingMail(to, SUBJECT, engine.process("user-invitation", context));
    }
}
