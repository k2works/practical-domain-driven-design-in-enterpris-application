package com.example.cargotracker.tracking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.OpenServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseAuthorization;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 受領期限を過ぎた有人案件の照会（Bolt 40-9。読むときの判定。時計を固定して確かめる）。 */
class ServiceCaseOverdueQueryServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c9001"));
    /** 月 2026-11-16 10:00 JST。通常の照会の受領期限は 14:00 JST（05:00Z）。 */
    private static final Instant OPENED_AT = Instant.parse("2026-11-16T01:00:00Z");

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();
    private int sequence;

    private static AuthenticatedActor staff(Role role) {
        return new AuthenticatedActor(new UserId(UUID.randomUUID()), COMPANY, Set.of(role), "担当", "A 社");
    }

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

    private CaseNumber open(ServiceCaseKind kind, ServicePriority priority) {
        ServiceCaseCommandService commands = new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(OPENED_AT, ZoneOffset.UTC));
        ServiceCaseDraft draft =
                ServiceCaseDraft.of(kind, priority, "原因 " + (++sequence), "CS").withShipper(COMPANY);
        return ((OpenServiceCaseOutcome.Opened)
                        commands.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(draft)))
                .number();
    }

    private ServiceCaseOverdueQueryService service(Instant now) {
        return new ServiceCaseOverdueQueryService(
                repository, Clock.fixed(now, ZoneOffset.UTC), ServiceCaseAuthorization::canOperate);
    }

    @Test
    void 行1_受領期限を過ぎた受付済みを期限の古い順に返し依頼元に担当未確定を示す() {
        CaseNumber normal = open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL); // 受領期限 05:00Z
        CaseNumber high = open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.HIGH); // 受領期限 02:00Z

        List<OverdueServiceCase> overdue = service(Instant.parse("2026-11-16T06:00:00Z"))
                .overdue(staff(Role.TRACKING_MANAGER))
                .orElseThrow();

        assertThat(overdue).extracting(OverdueServiceCase::number).containsExactly(high, normal);
        assertThat(overdue.getFirst().overdueBy()).isEqualTo(Duration.ofHours(4));
        assertThat(overdue.getFirst().status()).isEqualTo(ServiceCaseStatus.RECEIVED);
        assertThat(overdue.getFirst().requesterNotice().assigneePending()).isTrue();
    }

    @Test
    void 行2_期限の前と期限ちょうどは返さない() {
        open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL);

        assertThat(service(Instant.parse("2026-11-16T05:00:00Z")).overdue(staff(Role.TRACKING_MANAGER)))
                .contains(List.of());
    }

    @Test
    void 行3_受領した案件は期限を過ぎても返さない() {
        CaseNumber number = open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL);
        new ServiceCaseCommandService(
                        repository, issuer, (from, to) -> calendar(), Clock.fixed(OPENED_AT, ZoneOffset.UTC))
                .receive(staff(Role.CUSTOMER_SUPPORT), new ReceiveServiceCaseCommand(number));

        assertThat(service(Instant.parse("2026-11-20T00:00:00Z")).overdue(staff(Role.CUSTOMER_SUPPORT)))
                .contains(List.of());
    }

    @Test
    void 行4_上位対応中の案件は返す() {
        CaseNumber number = open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL);
        repository.cases.put(
                number.value(),
                repository
                        .cases
                        .get(number.value())
                        .escalate(new com.example.cargotracker.shared.domain.UtcInstant(
                                Instant.parse("2026-11-16T06:00:00Z")))
                        .orElseThrow());

        List<OverdueServiceCase> overdue = service(Instant.parse("2026-11-16T07:00:00Z"))
                .overdue(staff(Role.SALES))
                .orElseThrow();

        assertThat(overdue)
                .singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(ServiceCaseStatus.ESCALATED));
    }

    @Test
    void 行5_社内の運用役割でなければ権限なし() {
        open(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL);
        AuthenticatedActor shipper =
                new AuthenticatedActor(new UserId(UUID.randomUUID()), COMPANY, Set.of(Role.SHIPPER), "荷主", "荷主社");

        assertThat(service(Instant.parse("2026-11-20T00:00:00Z")).overdue(shipper))
                .isEmpty();
        assertThat(service(Instant.parse("2026-11-20T00:00:00Z")).overdue(staff(Role.AUDITOR)))
                .isEmpty();
    }
}
