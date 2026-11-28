package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.TotpEnrollmentMailTemplate;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 認証アプリの登録と再登録のお知らせの文面（UI 設計「認証アプリの登録のご案内」「認証アプリの再登録のご案内」。Bolt 38d）。Thymeleaf の
 * テキストのテンプレート（{@code classpath:mail/totp-enrolled.txt}・{@code mail/totp-re-enrolled.txt}）で本文を組み立てる（エンジンと
 * 日時の書式は {@link MailTextTemplates}）。
 */
public final class ThymeleafTotpEnrollmentMailTemplate implements TotpEnrollmentMailTemplate {

    /** 登録の件名（UI 設計）。 */
    static final String ENROLLED_SUBJECT = "【cargo-tracker】認証アプリの登録のご案内";

    /** 再登録の件名（UI 設計）。 */
    static final String RE_ENROLLED_SUBJECT = "【cargo-tracker】認証アプリの再登録のご案内";

    private final SpringTemplateEngine engine = MailTextTemplates.engine();

    @Override
    public OutgoingMail compose(String to, UtcInstant enrolledAt, boolean reEnrolled, String locale) {
        Context context = new Context(Locale.forLanguageTag(locale));
        context.setVariable("enrolledAt", MailTextTemplates.dateTime(enrolledAt));
        return reEnrolled
                ? new OutgoingMail(to, RE_ENROLLED_SUBJECT, engine.process("totp-re-enrolled", context))
                : new OutgoingMail(to, ENROLLED_SUBJECT, engine.process("totp-enrolled", context));
    }
}
