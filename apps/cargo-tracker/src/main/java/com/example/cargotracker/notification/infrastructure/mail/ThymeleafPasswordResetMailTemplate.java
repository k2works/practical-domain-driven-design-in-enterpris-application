package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.PasswordResetMailTemplate;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * password の再設定のメールの文面（UI 設計「password の再設定のメール」。Bolt 33）。Thymeleaf のテキストのテンプレート
 * （{@code classpath:mail/password-reset.txt}）で本文を組み立てる。画面の HTML のテンプレートとは別のエンジンにする（テキストとして出し、
 * HTML の escape をしない。式は画面と同じ SpEL）。期限は画面と同じ書式（今は Asia/Tokyo に固定。UI 設計）。
 */
public final class ThymeleafPasswordResetMailTemplate implements PasswordResetMailTemplate {

    /** 件名（UI 設計）。 */
    static final String SUBJECT = "【cargo-tracker】password の再設定のご案内";

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");

    private final SpringTemplateEngine engine;

    public ThymeleafPasswordResetMailTemplate() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        this.engine = new SpringTemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    @Override
    public OutgoingMail compose(String to, String resetUrl, UtcInstant expiresAt, String locale) {
        Context context = new Context(Locale.forLanguageTag(locale));
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiresAt", DATE_TIME.format(expiresAt.instant().atZone(DISPLAY_ZONE)));
        return new OutgoingMail(to, SUBJECT, engine.process("password-reset", context));
    }
}
