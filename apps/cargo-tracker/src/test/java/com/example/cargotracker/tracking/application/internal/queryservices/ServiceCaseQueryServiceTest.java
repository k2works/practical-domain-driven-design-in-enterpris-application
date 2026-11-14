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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 有人案件の一覧（S-14）と詳細（S-15）の照会（Bolt 40-11。読むときの判定。時計を固定して確かめる）。 */
class ServiceCaseQueryServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040b1101"));
    /** 月 2026-11-16 10:00 JST。通常の受領期限は 14:00 JST（05:00Z）、高は 11:00 JST（02:00Z）。 */
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

    private ServiceCaseCommandService commands() {
        return new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(OPENED_AT, ZoneOffset.UTC));
    }

    private CaseNumber open(ServiceCasePriorityKind spec) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(spec.kind, spec.priority, "原因 " + (++sequence), "CS")
                .withShipper(COMPANY);
        return ((OpenServiceCaseOutcome.Opened)
                        commands().open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(draft)))
                .number();
    }

    private record ServiceCasePriorityKind(ServiceCaseKind kind, ServicePriority priority) {}

    private static final ServiceCasePriorityKind NORMAL =
            new ServiceCasePriorityKind(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL);
    private static final ServiceCasePriorityKind HIGH =
            new ServiceCasePriorityKind(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.HIGH);

    private void receive(CaseNumber number) {
        commands().receive(staff(Role.CUSTOMER_SUPPORT), new ReceiveServiceCaseCommand(number));
    }

    private ServiceCaseQueryService service(Instant now) {
        return new ServiceCaseQueryService(
                repository, Clock.fixed(now, ZoneOffset.UTC), ServiceCaseAuthorization::canOperate);
    }

    @Test
    void 行1_既定の未終結は受領期限の古い順に受付済みと対応中を返し終結は返さない() {
        CaseNumber normal = open(NORMAL);
        CaseNumber high = open(HIGH);
        CaseNumber received = open(NORMAL);
        receive(received);
        CaseNumber closedOne = open(NORMAL);
        repository.markDuplicateClosed(closedOne);

        ServiceCaseList list = service(Instant.parse("2026-11-16T01:30:00Z"))
                .list(staff(Role.TRACKING_MANAGER), ServiceCaseListFilter.OPEN)
                .orElseThrow();

        assertThat(list.items()).extracting(ServiceCaseListItem::number).containsExactly(high, normal, received);
        assertThat(list.truncated()).isFalse();
    }

    @Test
    void 行2_未受領は受付済みと上位対応中だけ() {
        CaseNumber unreceived = open(NORMAL);
        CaseNumber received = open(NORMAL);
        receive(received);

        ServiceCaseList list = service(Instant.parse("2026-11-16T01:30:00Z"))
                .list(staff(Role.CUSTOMER_SUPPORT), ServiceCaseListFilter.UNRECEIVED)
                .orElseThrow();

        assertThat(list.items()).extracting(ServiceCaseListItem::number).containsExactly(unreceived);
        assertThat(list.items().getFirst().status()).isEqualTo(ServiceCaseStatus.RECEIVED);
    }

    @Test
    void 行3_対応中は受領した案件だけ() {
        open(NORMAL);
        CaseNumber received = open(NORMAL);
        receive(received);

        ServiceCaseList list = service(Instant.parse("2026-11-16T01:30:00Z"))
                .list(staff(Role.SALES), ServiceCaseListFilter.IN_PROGRESS)
                .orElseThrow();

        assertThat(list.items()).extracting(ServiceCaseListItem::number).containsExactly(received);
    }

    @Test
    void 行4_期限超過は期限を過ぎた受付済みだけ_期限ちょうどは超過でない() {
        CaseNumber high = open(HIGH); // 受領期限 02:00Z
        open(NORMAL); // 受領期限 05:00Z

        ServiceCaseList list = service(Instant.parse("2026-11-16T02:00:01Z"))
                .list(staff(Role.TRACKING_MANAGER), ServiceCaseListFilter.OVERDUE)
                .orElseThrow();
        ServiceCaseList atTheMoment = service(Instant.parse("2026-11-16T02:00:00Z"))
                .list(staff(Role.TRACKING_MANAGER), ServiceCaseListFilter.OVERDUE)
                .orElseThrow();

        assertThat(list.items()).extracting(ServiceCaseListItem::number).containsExactly(high);
        assertThat(list.items().getFirst().receiptOverdue()).isTrue();
        assertThat(atTheMoment.items()).isEmpty();
    }

    @Test
    void 行5_未終結の一覧でも期限を過ぎた受付済みには期限超過の印を付ける() {
        CaseNumber high = open(HIGH);
        CaseNumber normal = open(NORMAL);

        ServiceCaseList list = service(Instant.parse("2026-11-16T03:00:00Z"))
                .list(staff(Role.TRACKING_MANAGER), ServiceCaseListFilter.OPEN)
                .orElseThrow();

        assertThat(list.items())
                .extracting(ServiceCaseListItem::number, ServiceCaseListItem::receiptOverdue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(high, true),
                        org.assertj.core.groups.Tuple.tuple(normal, false));
    }

    @Test
    void 行6_社内の運用役割でなければ権限なし() {
        open(NORMAL);
        AuthenticatedActor shipper =
                new AuthenticatedActor(new UserId(UUID.randomUUID()), COMPANY, Set.of(Role.SHIPPER), "荷主", "A 社");

        assertThat(service(OPENED_AT).list(shipper, ServiceCaseListFilter.OPEN)).isEmpty();
        assertThat(service(OPENED_AT).list(staff(Role.SYSTEM_ADMIN), ServiceCaseListFilter.OPEN))
                .isEmpty();
    }

    @Test
    void 行7_詳細は案件と期限超過の判定を返す() {
        CaseNumber high = open(HIGH);

        Optional<ServiceCaseDetail> detail =
                service(Instant.parse("2026-11-16T03:00:00Z")).detail(staff(Role.ROUTE_DESIGNER), high);

        assertThat(detail).isPresent();
        assertThat(detail.get().serviceCase().number()).isEqualTo(high);
        assertThat(detail.get().receiptOverdue()).isTrue();
    }

    @Test
    void 行8_詳細はない案件と権限のない役割で空() {
        CaseNumber number = open(NORMAL);

        assertThat(service(OPENED_AT).detail(staff(Role.TRACKING_MANAGER), new CaseNumber("SC-2026-9999")))
                .isEmpty();
        assertThat(service(OPENED_AT).detail(staff(Role.SYSTEM_ADMIN), number)).isEmpty();
    }

    @Test
    void 行8b_詳細は案件ごとの認可を通す_自社の荷主は見え他社の荷主は空_役割の述語だけを広げても他社の案件は見えない() {
        CaseNumber number = open(NORMAL);
        AuthenticatedActor own =
                new AuthenticatedActor(new UserId(UUID.randomUUID()), COMPANY, Set.of(Role.SHIPPER), "自社の荷主", "A 社");
        AuthenticatedActor other = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040b1188")),
                Set.of(Role.SHIPPER),
                "他社の荷主",
                "B 社");
        // 役割の述語を広げて、荷主担当者もはじめの関門を通るようにした照会（荷主に詳細を開く W9 の想定）
        ServiceCaseQueryService widened =
                new ServiceCaseQueryService(repository, Clock.fixed(OPENED_AT, ZoneOffset.UTC), actor -> true);

        assertThat(widened.detail(own, number)).isPresent();
        assertThat(widened.detail(other, number)).isEmpty();
    }

    @Test
    void 行9_件数の上限を超えるときは打ち切りを示す() {
        for (int i = 0; i < ServiceCaseQueryService.LIMIT + 1; i++) {
            open(NORMAL);
        }

        ServiceCaseList list = service(OPENED_AT)
                .list(staff(Role.TRACKING_MANAGER), ServiceCaseListFilter.OPEN)
                .orElseThrow();

        assertThat(list.items()).hasSize(ServiceCaseQueryService.LIMIT);
        assertThat(list.truncated()).isTrue();
        assertThat(list.limit()).isEqualTo(ServiceCaseQueryService.LIMIT);
    }
}
