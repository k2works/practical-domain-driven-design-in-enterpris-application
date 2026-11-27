package com.example.cargotracker.externaldata.interfaces.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.externaldata.application.internal.queryservices.MissingIngestionQueryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

class MissingIngestionSchedulerTest {

    private final MissingIngestionQueryService service = mock(MissingIngestionQueryService.class);
    private final MissingIngestionScheduler scheduler = new MissingIngestionScheduler(service);
    private final Logger logger = (Logger) LoggerFactory.getLogger(MissingIngestionScheduler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void startLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 当日の取込がなければ警告の印を付けたWARNを1つ出す() {
        given(service.isTodayMissing()).willReturn(true);

        scheduler.run();

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains("[ALERT:ingestion-missing]");
        });
    }

    @Test
    void 不足がなければ何も出さない() {
        given(service.isTodayMissing()).willReturn(false);

        scheduler.run();

        assertThat(logs.list).isEmpty();
    }

    @Test
    void 照会が失敗しても例外を外へ出さずERRORを残す() {
        given(service.isTodayMissing()).willThrow(new IllegalStateException("DB に接続できない"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();

        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void 毎日日本時間の11時に1つのインスタンスだけが動く設定である() throws NoSuchMethodException {
        var run = MissingIngestionScheduler.class.getMethod("run");

        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("0 0 11 * * *");
        assertThat(run.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Tokyo");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("missing-ingestion-alert");
    }
}
