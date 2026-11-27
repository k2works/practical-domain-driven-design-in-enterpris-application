package com.example.cargotracker.operations.interfaces.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.operations.application.internal.queryservices.EventBacklogQueryService;
import com.example.cargotracker.operations.domain.model.valueobjects.BacklogSeverity;
import com.example.cargotracker.operations.domain.model.valueobjects.EventBacklog;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

class EventBacklogMonitorTest {

    private final EventBacklogQueryService service = mock(EventBacklogQueryService.class);
    private final EventBacklogMonitor monitor = new EventBacklogMonitor(service);
    private final Logger logger = (Logger) LoggerFactory.getLogger(EventBacklogMonitor.class);
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
    void 滞留がなければ何も出さない() {
        given(service.current()).willReturn(new EventBacklog(0, Duration.ZERO, BacklogSeverity.NONE));

        monitor.run();

        assertThat(logs.list).isEmpty();
    }

    @Test
    void 警告はWARNに警告の印と件数と最も古い経過分を出す() {
        given(service.current()).willReturn(new EventBacklog(2, Duration.ofMinutes(7), BacklogSeverity.WARNING));

        monitor.run();

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .contains("[ALERT:event-backlog-warning]", "2 件", "7 分")
                    .doesNotContain("[ALERT:event-backlog-critical]");
        });
    }

    @Test
    void 重大はERRORに重大の印だけを出す() {
        given(service.current()).willReturn(new EventBacklog(10, Duration.ofMinutes(31), BacklogSeverity.CRITICAL));

        monitor.run();

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.ERROR);
            assertThat(log.getFormattedMessage())
                    .contains("[ALERT:event-backlog-critical]")
                    .doesNotContain("[ALERT:event-backlog-warning]");
        });
    }

    @Test
    void 照会が失敗しても例外を外へ出さずERRORを残す() {
        given(service.current()).willThrow(new IllegalStateException("DB に接続できない"));

        assertThatCode(monitor::run).doesNotThrowAnyException();

        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void 毎5分に1つのインスタンスだけが動く設定である() throws NoSuchMethodException {
        var run = EventBacklogMonitor.class.getMethod("run");

        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("0 */5 * * * *");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("event-backlog-monitor");
    }
}
