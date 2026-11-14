package com.example.cargotracker.quotation.interfaces.scheduled;

import com.example.cargotracker.quotation.application.internal.commandservices.QuotationExpiryNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 見積りの有効期限の 24 時間前の検出を 1 時間ごとに起動する（US-22 AC2。W9 の Bolt 43-2）。毎時 0 分に動く。複数のインスタンスで動かすと二重に検出するが、
 * 宛先の側の一意制約が 2 通を防ぐ（排他は W10 の運用準備で足す。ShedLock）。検出の失敗は次の時間まで再試行しない（その時間の見積りは通知されない）ので、
 * 失敗は ERROR で残す。止めたいときは {@code cargotracker.quotation.expiry-notification.enabled=false}。
 */
@Component
@ConditionalOnProperty(
        prefix = "cargotracker.quotation.expiry-notification",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class QuotationExpiryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationExpiryScheduler.class);

    private final QuotationExpiryNotificationService service;

    public QuotationExpiryScheduler(QuotationExpiryNotificationService service) {
        this.service = service;
    }

    /** 毎時 0 分に、有効期限が 24 時間前の窓にある見積りを検出する。 */
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        try {
            int published = service.detect();
            LOG.info("見積りの有効期限の通知を検出した: {} 件", published);
        } catch (RuntimeException e) {
            LOG.error("見積りの有効期限の通知の検出に失敗した", e);
        }
    }
}
