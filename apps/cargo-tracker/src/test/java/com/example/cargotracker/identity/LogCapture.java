package com.example.cargotracker.identity;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * テストの間だけ、すべてのロガーの出力（メッセージと例外の連なり）を取り込む（Bolt 31d の開発レビュー）。クラス単位の
 * {@code OutputCaptureExtension} は大きな統合テストのクラスの出力をすべてメモリに溜めて CI で OutOfMemoryError を起こしたので、
 * 見張りたい操作の間だけ付ける。
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    public static LogCapture start() {
        return new LogCapture();
    }

    /** 取り込んだ行（メッセージと例外の連なり）を 1 つの文字列にする。 */
    public String text() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        + (event.getThrowableProxy() == null
                                ? ""
                                : System.lineSeparator() + ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
