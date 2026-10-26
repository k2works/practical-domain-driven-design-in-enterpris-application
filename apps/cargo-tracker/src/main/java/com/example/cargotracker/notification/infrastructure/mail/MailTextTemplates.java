package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * メールの文面の Thymeleaf のエンジンと日時の書式（Bolt 38b2b。再設定と利用者の招待の文面で共有する）。テキストのテンプレート
 * （{@code classpath:mail/*.txt}）を読み、画面の HTML のテンプレートとは別のエンジンにする（テキストとして出し、HTML の escape をしない。
 * 式は画面と同じ SpEL）。日時は画面と同じ書式（今は Asia/Tokyo に固定。UI 設計）。
 */
final class MailTextTemplates {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");

    private MailTextTemplates() {}

    /** {@code mail/} のテキストのテンプレートを読むエンジン。 */
    static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** 期限などの日時の画面と同じ書式。 */
    static String dateTime(UtcInstant at) {
        return DATE_TIME.format(at.instant().atZone(DISPLAY_ZONE));
    }
}
