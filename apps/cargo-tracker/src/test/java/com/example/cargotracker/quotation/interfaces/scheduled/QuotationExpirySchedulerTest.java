package com.example.cargotracker.quotation.interfaces.scheduled;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.quotation.application.internal.commandservices.QuotationExpiryNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** 有効期限の 24 時間前の検出の起動（Bolt 43-2）。毎時 0 分に動き、検出の失敗は例外にせず次の時間に任せる。 */
class QuotationExpirySchedulerTest {

    private final QuotationExpiryNotificationService service = mock(QuotationExpiryNotificationService.class);
    private final QuotationExpiryScheduler scheduler = new QuotationExpiryScheduler(service);

    @Test
    void 起動すると検出を1回呼ぶ() {
        scheduler.run();

        then(service).should().detect();
    }

    @Test
    void 検出が失敗しても例外を外へ出さない() {
        given(service.detect()).willThrow(new IllegalStateException("DB に接続できない"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }

    @Test
    void 毎時0分に動く設定である() throws NoSuchMethodException {
        Scheduled scheduled = QuotationExpiryScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        org.assertj.core.api.Assertions.assertThat(scheduled.cron()).isEqualTo("0 0 * * * *");
    }
}
