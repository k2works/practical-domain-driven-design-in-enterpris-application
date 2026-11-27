package com.example.cargotracker.notification.interfaces.scheduled;

import com.example.cargotracker.notification.application.internal.commandservices.ShipperNotificationRetryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 失敗した荷主への業務の通知の再送を 10 分ごと（毎時 5 分から）に起動する（ADR-021。Bolt 61）。止めたいときは
 * {@code cargotracker.notification.retry.enabled=false}。
 */
@Component
@ConditionalOnProperty(
        prefix = "cargotracker.notification.retry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ShipperNotificationRetryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ShipperNotificationRetryScheduler.class);

    private final ShipperNotificationRetryService service;

    public ShipperNotificationRetryScheduler(ShipperNotificationRetryService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 5/10 * * * *")
    @SchedulerLock(name = "shipper-notification-retry", lockAtMostFor = "PT9M", lockAtLeastFor = "PT30S")
    public void run() {
        try {
            int attempted = service.retryDue();
            if (attempted > 0) {
                LOG.info("失敗した荷主への通知を送り直した: {} 件", attempted);
            }
        } catch (RuntimeException e) {
            LOG.error("荷主への通知の再送に失敗した", e);
        }
    }
}
