package com.example.cargotracker.notification.interfaces.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.notification.application.internal.commandservices.ShipperNotificationRetryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ShipperNotificationRetrySchedulerTest {

    private final ShipperNotificationRetryService service = mock(ShipperNotificationRetryService.class);
    private final ShipperNotificationRetryScheduler scheduler = new ShipperNotificationRetryScheduler(service);

    @Test
    void 実行すると再送を起動する() {
        scheduler.run();

        verify(service).retryDue();
    }

    @Test
    void 再送が失敗しても例外を外へ出さない() {
        given(service.retryDue()).willThrow(new IllegalStateException("DB に接続できない"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }

    @Test
    void 毎10分に1つのインスタンスだけが動く設定である() throws NoSuchMethodException {
        var run = ShipperNotificationRetryScheduler.class.getMethod("run");

        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("0 5/10 * * * *");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("shipper-notification-retry");
    }
}
