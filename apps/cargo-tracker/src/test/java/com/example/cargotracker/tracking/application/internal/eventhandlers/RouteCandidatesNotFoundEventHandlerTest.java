package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.events.RouteCandidatesNotFound;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DE-32 経路候補が見つからなかった を受けて有人案件を起票する（UC-06 3a。Bolt 40-14）。 */
class RouteCandidatesNotFoundEventHandlerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040e1402"));
    private static final UserId DESIGNER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040e1403"));

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();

    private static BusinessCalendar calendar() {
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 11, 1);
                !date.isAfter(LocalDate.of(2027, 1, 31));
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(
                    weekend
                            ? BusinessDay.closed(date, JST, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
        }
        return BusinessCalendar.of(days);
    }

    private RouteCandidatesNotFoundEventHandler handler() {
        return new RouteCandidatesNotFoundEventHandler(new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static RouteCandidatesNotFound event(String reason) {
        return eventAt(reason, NOW);
    }

    /** 算出した時刻を指定した DE-32（再算出は時刻が違う）。 */
    private static RouteCandidatesNotFound eventAt(String reason, Instant notFoundAt) {
        return new RouteCandidatesNotFound(
                UUID.fromString("00000000-0000-0000-0000-0000040e1404"),
                "RC-2026-0042",
                UUID.fromString("00000000-0000-0000-0000-0000040e1405"),
                UUID.fromString("00000000-0000-0000-0000-0000040e1406"),
                1,
                SHIPPER,
                reason,
                reason.equals("EXTERNAL_DATA_MISSING") ? 0 : 3,
                new UtcInstant(notFoundAt),
                DESIGNER);
    }

    @Test
    void 行1_基本制約を満たす候補なしは見積り相談の有人案件を荷主企業つきで起票する() {
        handler().on(event("NO_CONFORMING_CANDIDATE"));

        assertThat(repository.cases.values()).singleElement().satisfies(serviceCase -> {
            assertThat(serviceCase.kind()).isEqualTo(ServiceCaseKind.QUOTATION_CONSULTATION);
            assertThat(serviceCase.priority()).isEqualTo(ServicePriority.NORMAL);
            assertThat(serviceCase.department()).isEqualTo("経路設計部");
            assertThat(serviceCase.shipperCompanyId()).contains(SHIPPER);
            assertThat(serviceCase.cause()).contains("RC-2026-0042");
        });
    }

    @Test
    void 行2_外部データの欠落は外部停止の有人案件を高の優先度で起票し復旧照合を要る案件にする() {
        handler().on(event("EXTERNAL_DATA_MISSING"));

        assertThat(repository.cases.values()).singleElement().satisfies(serviceCase -> {
            assertThat(serviceCase.kind()).isEqualTo(ServiceCaseKind.EXTERNAL_OUTAGE);
            assertThat(serviceCase.priority()).isEqualTo(ServicePriority.HIGH);
            assertThat(serviceCase.reconciliationRequired()).isTrue();
            assertThat(serviceCase.manualSourceRef())
                    .hasValueSatisfying(ref -> assertThat(ref).contains("RC-2026-0042"));
            assertThat(serviceCase.shipperCompanyId()).contains(SHIPPER);
        });
    }

    @Test
    void 行3_同じ経路設計案件の再算出で二重に起票しない() {
        handler().on(event("NO_CONFORMING_CANDIDATE"));
        handler().on(event("NO_CONFORMING_CANDIDATE"));

        assertThat(repository.cases).hasSize(1);
    }

    @Test
    void 行4_営業カレンダーが未登録でも既定のカレンダーで期限を計算して起票する() {
        RouteCandidatesNotFoundEventHandler handler =
                new RouteCandidatesNotFoundEventHandler(new ServiceCaseCommandService(
                        repository,
                        issuer,
                        (from, to) -> {
                            throw new BusinessCalendarNotRegisteredException(from);
                        },
                        Clock.fixed(NOW, ZoneOffset.UTC)));
        RouteCandidatesNotFound event = event("NO_CONFORMING_CANDIDATE");

        handler.on(event);

        assertThat(repository.cases).hasSize(1);
    }

    @Test
    void 行4b_同じ算出の再配信は案件を終結した後でも増やさず_再算出は終結後なら新しい案件になる() {
        RouteCandidatesNotFoundEventHandler handler = handler();
        handler.on(event("NO_CONFORMING_CANDIDATE"));
        repository.markDuplicateClosed(
                repository.cases.values().iterator().next().number());

        handler.on(event("NO_CONFORMING_CANDIDATE"));
        assertThat(repository.cases).hasSize(1);

        handler.on(eventAt("NO_CONFORMING_CANDIDATE", Instant.parse("2026-11-16T02:00:00Z")));
        assertThat(repository.cases).hasSize(2);
    }

    @Test
    void 行5_知らない理由は例外を上げる() {
        RouteCandidatesNotFoundEventHandler handler = handler();
        RouteCandidatesNotFound event = event("SOMETHING_ELSE");

        assertThatThrownBy(() -> handler.on(event)).isInstanceOf(IllegalArgumentException.class);
    }
}
