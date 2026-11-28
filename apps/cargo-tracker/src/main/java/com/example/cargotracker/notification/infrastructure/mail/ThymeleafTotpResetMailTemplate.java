package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.TotpResetMailTemplate;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 管理者による TOTP の登録の解除のお知らせの文面（UI 設計「認証アプリの登録の解除のご案内」。Bolt 38d）。Thymeleaf のテキストの
 * テンプレート（{@code classpath:mail/totp-reset.txt}）で本文を組み立てる（エンジンと日時の書式は {@link MailTextTemplates}）。
 */
public final class ThymeleafTotpResetMailTemplate implements TotpResetMailTemplate {

    /** 件名（UI 設計）。 */
    static final String SUBJECT = "【cargo-tracker】認証アプリの登録の解除のご案内";

    private final SpringTemplateEngine engine = MailTextTemplates.engine();

    @Override
    public OutgoingMail compose(String to, UtcInstant resetAt, String locale) {
        Context context = new Context(Locale.forLanguageTag(locale));
        context.setVariable("resetAt", MailTextTemplates.dateTime(resetAt));
        return new OutgoingMail(to, SUBJECT, engine.process("totp-reset", context));
    }
}
