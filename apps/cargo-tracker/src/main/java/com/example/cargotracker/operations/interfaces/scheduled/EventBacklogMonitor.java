package com.example.cargotracker.operations.interfaces.scheduled;

import com.example.cargotracker.operations.application.internal.queryservices.EventBacklogQueryService;
import com.example.cargotracker.operations.domain.model.valueobjects.EventBacklog;
import com.example.cargotracker.shared.domain.OperationsAlert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未完了のイベント配信の滞留を 5 分ごとに調べ、あれば警告の印を付けたログを出す（OBS-03、RB-04。Bolt 57-2）。滞留が続く間は
 * 5 分ごとに出す。止めたいときは {@code cargotracker.operations.event-backlog.enabled=false}。
 */
@Component
@ConditionalOnProperty(
        prefix = "cargotracker.operations.event-backlog",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventBacklogMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(EventBacklogMonitor.class);

    private final EventBacklogQueryService service;

    public EventBacklogMonitor(EventBacklogQueryService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "event-backlog-monitor", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void run() {
        try {
            report(service.current());
        } catch (RuntimeException e) {
            LOG.error("未完了のイベント配信の検知に失敗した", e);
        }
    }

    private static void report(EventBacklog backlog) {
        long oldestMinutes = backlog.oldestAge().toMinutes();
        switch (backlog.severity()) {
            default -> {
                // 滞留はない
            }
            case WARNING ->
                LOG.warn(
                        "{} 未完了のイベント配信が滞留している: {} 件、最も古い発行から {} 分",
                        OperationsAlert.EVENT_BACKLOG_WARNING.marker(),
                        backlog.staleCount(),
                        oldestMinutes);
            case CRITICAL ->
                LOG.error(
                        "{} 未完了のイベント配信が滞留している: {} 件、最も古い発行から {} 分",
                        OperationsAlert.EVENT_BACKLOG_CRITICAL.marker(),
                        backlog.staleCount(),
                        oldestMinutes);
        }
    }
}
