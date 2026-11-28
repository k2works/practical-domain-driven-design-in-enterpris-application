package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.QuotationConsultationRequested;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commandservices.SystemServiceCaseOpener;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
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

/** 荷主が見積りの条件を相談した を受けて有人案件を起票する（US-24 AC3。W9 の Bolt 46-1）。 */
class QuotationConsultationRequestedEventHandlerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000046e1401"));
    private static final UserId REQUESTER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000046e1402"));
    private static final UUID QUOTATION_ID = UUID.fromString("00000000-0000-0000-0000-0000046e1403");

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

    private QuotationConsultationRequestedEventHandler handler() {
        return new QuotationConsultationRequestedEventHandler(new SystemServiceCaseOpener(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static QuotationConsultationRequested event(String content, Instant requestedAt) {
        return new QuotationConsultationRequested(
                QUOTATION_ID,
                1,
                UUID.fromString("00000000-0000-0000-0000-0000046e1404"),
                "TR-2026-0001",
                SHIPPER.value(),
                REQUESTER.value(),
                content,
                new UtcInstant(requestedAt));
    }

    @Test
    void 相談を受けると見積り相談の有人案件を荷主企業と相談内容つきで起票する() {
        handler().on(event("納期を 3 日早められるか", NOW));

        assertThat(repository.cases.values()).singleElement().satisfies(serviceCase -> {
            assertThat(serviceCase.kind()).isEqualTo(ServiceCaseKind.QUOTATION_CONSULTATION);
            assertThat(serviceCase.priority()).isEqualTo(ServicePriority.NORMAL);
            assertThat(serviceCase.department()).isEqualTo("営業部");
            assertThat(serviceCase.shipperCompanyId()).contains(SHIPPER);
            assertThat(serviceCase.cause()).contains("TR-2026-0001 見積 1").contains("納期を 3 日早められるか");
        });
    }

    @Test
    void 同じ相談の再配信で二重に起票しない() {
        handler().on(event("納期を 3 日早められるか", NOW));
        handler().on(event("納期を 3 日早められるか", NOW));

        assertThat(repository.cases.values()).hasSize(1);
    }

    @Test
    void 内容の違う相談は別の有人案件にする() {
        handler().on(event("納期を 3 日早められるか", NOW));
        handler().on(event("支払条件を変えられるか", NOW.plusSeconds(60)));

        assertThat(repository.cases.values()).hasSize(2);
    }
}
