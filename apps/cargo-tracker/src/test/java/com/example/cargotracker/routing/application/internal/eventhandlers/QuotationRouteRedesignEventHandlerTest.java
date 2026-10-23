package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteRedesigns;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * DE-06 を受けて見積りの公開 API で再設計要を伝える（US-07 AC5、ADR-014。Bolt 37e2）。値は DE-06 の項目から取り、案件を読まない。
 * 業務の理由で付けなかったときは警告のログだけを残し、楽観ロックの競合は上げて発行記録を未完了に残す（DE-05 の前例）。
 * 状態の組み合わせの表の行 1〜5。
 */
class QuotationRouteRedesignEventHandlerTest {

    private static final UtcInstant REQUIRED_AT = new UtcInstant(Instant.parse("2026-10-07T06:00:00Z"));

    private final List<RouteRedesignNotificationRequest> requests = new ArrayList<>();
    private RouteRedesignNotificationReceipt receipt = new RouteRedesignNotificationReceipt.Marked(1);
    private RuntimeException failure;
    private final QuotationRouteRedesignEventHandler handler =
            new QuotationRouteRedesignEventHandler(new QuotationRouteRedesigns(request -> {
                requests.add(request);
                if (failure != null) {
                    throw failure;
                }
                return receipt;
            }));
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuotationRouteRedesignEventHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 案件番号と経路版番号と再設計要にした時刻を見積りに渡して印を付ける() {
        // 行 1
        handler.on(event());

        assertThat(requests).containsExactly(new RouteRedesignNotificationRequest("RC-2026-0001", 1, REQUIRED_AT));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void すでに印が付いていればログを残さない() {
        // 行 2
        receipt = new RouteRedesignNotificationReceipt.AlreadyMarked();

        handler.on(event());

        assertThat(logs.list).isEmpty();
    }

    @Test
    void 割り当てた見積りがなければ警告のログに残しDE05のlistenerが続けて伝えると書く() {
        // 行 3（DE-06 が DE-05 より先に届いた）
        receipt = new RouteRedesignNotificationReceipt.NotMarked(
                RouteRedesignNotificationReceipt.NotMarked.QUOTATION_NOT_FOUND);

        handler.on(event());

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .contains("RC-2026-0001")
                    .contains("QUOTATION_NOT_FOUND")
                    .contains("DE-05 の listener が続けて伝える");
        });
    }

    @Test
    void 不正な依頼と返されたら警告のログに残す() {
        // 行 4
        receipt = new RouteRedesignNotificationReceipt.NotMarked(
                RouteRedesignNotificationReceipt.NotMarked.INVALID_REQUEST);

        handler.on(event());

        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains("INVALID_REQUEST");
        });
    }

    @Test
    void 見積りの楽観ロックの競合は握りつぶさずに上げる() {
        // 行 5（listener のトランザクションごと戻し、発行記録を未完了に残す）
        failure = new IllegalStateException("競合");

        assertThatThrownBy(() -> handler.on(event())).isSameAs(failure);
    }

    private static RouteRedesignRequired event() {
        return new RouteRedesignRequired(
                UUID.randomUUID(),
                "RC-2026-0001",
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                REQUIRED_AT,
                List.of("DEADLINE_EXCEEDED"),
                List.of("V-201@1", "V-301@1"));
    }
}
