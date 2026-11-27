package com.example.cargotracker.externaldata.interfaces.scheduled;

import com.example.cargotracker.externaldata.application.internal.queryservices.MissingIngestionQueryService;
import com.example.cargotracker.shared.domain.OperationsAlert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 毎日 11:00（日本時間）に、当日の外部原本の取込がないかを調べ、なければ警告の印を付けたログを出す（FRS-04、RB-06。Bolt 58）。
 * 追跡・データ責任者へのメールは CloudWatch と SNS が送る。11:00 にアプリが止まっていた日は調べない。止めたいときは
 * {@code cargotracker.externaldata.missing-ingestion.enabled=false}。
 */
@Component
@ConditionalOnProperty(
        prefix = "cargotracker.externaldata.missing-ingestion",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MissingIngestionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MissingIngestionScheduler.class);

    private final MissingIngestionQueryService service;

    public MissingIngestionScheduler(MissingIngestionQueryService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Tokyo")
    @SchedulerLock(name = "missing-ingestion-alert", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            if (service.isTodayMissing()) {
                LOG.warn("{} 当日の外部原本の取込がない", OperationsAlert.INGESTION_MISSING.marker());
            }
        } catch (RuntimeException e) {
            LOG.error("当日の取込の確認に失敗した", e);
        }
    }
}
