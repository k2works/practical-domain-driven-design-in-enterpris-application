package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 有人案件の起票と受領のコマンドサービス（Bolt 40-7。US-20 AC1・AC2・AC8。認可の仮の定めは台帳 D-159）。 */
class ServiceCaseCommandServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final CompanyId OPERATOR_COMPANY =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c7001"));
    private static final CompanyId SHIPPER_A = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c7002"));
    private static final CompanyId SHIPPER_B = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c7003"));
    private static final UUID BOOKING = UUID.fromString("00000000-0000-0000-0000-0000040c7004");
    /** 月 2026-11-16 10:00 JST（営業時間内）。 */
    private static final Instant MONDAY_10 = Instant.parse("2026-11-16T01:00:00Z");
    /** 月 2026-11-16 19:00 JST（営業時間外）。 */
    private static final Instant MONDAY_19 = Instant.parse("2026-11-16T10:00:00Z");

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();

    private static AuthenticatedActor staff(Role role) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()), OPERATOR_COMPANY, Set.of(role), "担当 " + role, "A 社");
    }

    private static AuthenticatedActor shipper(CompanyId company) {
        return new AuthenticatedActor(new UserId(UUID.randomUUID()), company, Set.of(Role.SHIPPER), "荷主", "荷主社");
    }

    private static BusinessCalendar calendar() {
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 11, 1);
                !date.isAfter(LocalDate.of(2027, 1, 31));
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(
                    weekend || date.equals(LocalDate.of(2026, 11, 23))
                            ? BusinessDay.closed(date, JST, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
        }
        return BusinessCalendar.of(days);
    }

    private ServiceCaseCommandService service(Instant now) {
        return new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static ServiceCaseDraft inquiry(CompanyId company) {
        return ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "荷主の照会", "CS")
                .withShipper(company)
                .withBooking(BOOKING);
    }

    private static ServiceCaseDraft outage() {
        return ServiceCaseDraft.of(ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "外部停止", "運用")
                .withManualSource("manual-1");
    }

    // --- 起票: 認可 ---

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"CUSTOMER_SUPPORT", "SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER", "DATA_STEWARD"})
    void 行1_社内の運用役割はどの種類も起票できる(Role role) {
        OpenServiceCaseOutcome outcome = service(MONDAY_10).open(staff(role), new OpenServiceCaseCommand(outage()));

        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        assertThat(repository.cases).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"SYSTEM_ADMIN", "AUDITOR"})
    void 行2_システム管理者と監査担当者は起票できない(Role role) {
        OpenServiceCaseOutcome outcome = service(MONDAY_10).open(staff(role), new OpenServiceCaseCommand(outage()));

        assertThat(outcome).isEqualTo(new OpenServiceCaseOutcome.NotPermitted());
        assertThat(repository.cases).isEmpty();
        assertThat(issuer.issued).isZero();
    }

    @Test
    void 行3_荷主担当者は自社の顧客照会を起票できる() {
        OpenServiceCaseOutcome outcome =
                service(MONDAY_10).open(shipper(SHIPPER_A), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
    }

    static Stream<ServiceCaseDraft> shipperForbidden() {
        return Stream.of(
                inquiry(SHIPPER_B), // 他社を指す
                ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "原因", "CS"), // 荷主企業なし
                ServiceCaseDraft.of(ServiceCaseKind.AMENDMENT, ServicePriority.NORMAL, "変更", "運用")
                        .withShipper(SHIPPER_A)
                        .withBooking(BOOKING)
                        .withVersions("V1", "V2")); // 照会・見積り相談以外の種類
    }

    @ParameterizedTest
    @MethodSource("shipperForbidden")
    void 行4_荷主担当者は他社の分と照会以外の種類を起票できない(ServiceCaseDraft draft) {
        OpenServiceCaseOutcome outcome = service(MONDAY_10).open(shipper(SHIPPER_A), new OpenServiceCaseCommand(draft));

        assertThat(outcome).isEqualTo(new OpenServiceCaseOutcome.NotPermitted());
        assertThat(repository.cases).isEmpty();
    }

    // --- 起票: 必須項目・重複・期限 ---

    @Test
    void 行5_必須項目が足りなければ引き継がず不足を返し番号を使わない() {
        ServiceCaseDraft incomplete =
                ServiceCaseDraft.of(ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "外部停止", "運用");

        OpenServiceCaseOutcome outcome =
                service(MONDAY_10).open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(incomplete));

        assertThat(outcome).isEqualTo(new OpenServiceCaseOutcome.Incomplete(Set.of(MissingItem.MANUAL_SOURCE)));
        assertThat(repository.cases).isEmpty();
        assertThat(issuer.issued).isZero();
    }

    @Test
    void 行6_同じ荷主企業と予約と内容の未終結の案件があれば起票せず既存の受付番号を示す() {
        ServiceCaseCommandService service = service(MONDAY_10);
        OpenServiceCaseOutcome.Opened first = (OpenServiceCaseOutcome.Opened)
                service.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        OpenServiceCaseOutcome second =
                service.open(shipper(SHIPPER_A), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        assertThat(second).isEqualTo(new OpenServiceCaseOutcome.Duplicate(first.number()));
        assertThat(repository.cases).hasSize(1);
        assertThat(issuer.issued).isEqualTo(1);
    }

    @Test
    void 行6_他社の同じ内容は重複にせず別の案件として起票する() {
        ServiceCaseCommandService service = service(MONDAY_10);
        service.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        OpenServiceCaseOutcome other = service.open(shipper(SHIPPER_B), new OpenServiceCaseCommand(inquiry(SHIPPER_B)));

        assertThat(other).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        assertThat(repository.cases).hasSize(2);
    }

    @Test
    void 行6_既存の案件が終結していれば重複にしない() {
        ServiceCaseCommandService service = service(MONDAY_10);
        OpenServiceCaseOutcome.Opened first = (OpenServiceCaseOutcome.Opened)
                service.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));
        repository.markDuplicateClosed(first.number());

        OpenServiceCaseOutcome second =
                service.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        assertThat(second).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
    }

    @Test
    void 行7_営業時間内の通常の起票は受付番号と期限を返す() {
        OpenServiceCaseOutcome.Opened opened = (OpenServiceCaseOutcome.Opened)
                service(MONDAY_10).open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        assertThat(opened.number()).isEqualTo(CaseNumber.of(2026, 1));
        // 通常: 受領 4 営業時間（10:00 → 14:00 JST）、回答は 2 営業日後の同じ時刻
        assertThat(opened.receiveDueAt()).isEqualTo(new UtcInstant(Instant.parse("2026-11-16T05:00:00Z")));
        assertThat(opened.answerDueAt()).isEqualTo(new UtcInstant(Instant.parse("2026-11-18T01:00:00Z")));
        assertThat(opened.acknowledgedAt()).isEmpty();
    }

    @Test
    void 行8_営業時間外の優先度高は直ちに受付番号と翌営業日10時の次回更新日時を返す() {
        OpenServiceCaseOutcome.Opened opened = (OpenServiceCaseOutcome.Opened)
                service(MONDAY_19).open(staff(Role.TRACKING_MANAGER), new OpenServiceCaseCommand(outage()));

        // 翌営業日 2026-11-17 10:00 JST = 01:00Z
        assertThat(opened.nextUpdateAt()).isEqualTo(new UtcInstant(Instant.parse("2026-11-17T01:00:00Z")));
        assertThat(opened.acknowledgedAt()).contains(new UtcInstant(MONDAY_19));
    }

    @Test
    void 行9_営業日カレンダーの登録がなければ登録漏れとして返し番号を使わない() {
        ServiceCaseCommandService service = new ServiceCaseCommandService(
                repository,
                issuer,
                (from, to) -> {
                    throw new com.example.cargotracker.tracking.domain.model.valueobjects
                            .BusinessCalendarNotRegisteredException(from);
                },
                Clock.fixed(MONDAY_10, ZoneOffset.UTC));

        OpenServiceCaseOutcome outcome =
                service.open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A)));

        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.CalendarNotRegistered.class);
        assertThat(repository.cases).isEmpty();
        assertThat(issuer.issued).isZero();
    }

    // --- 受領 ---

    private CaseNumber openedNumber() {
        return ((OpenServiceCaseOutcome.Opened) service(MONDAY_10)
                        .open(staff(Role.CUSTOMER_SUPPORT), new OpenServiceCaseCommand(inquiry(SHIPPER_A))))
                .number();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"CUSTOMER_SUPPORT", "SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER", "DATA_STEWARD"})
    void 行10_社内の運用役割は受領でき担当者と時刻を記録して対応中になる(Role role) {
        CaseNumber number = openedNumber();
        AuthenticatedActor actor = staff(role);

        ReceiveServiceCaseOutcome outcome = service(MONDAY_10).receive(actor, new ReceiveServiceCaseCommand(number));

        assertThat(outcome).isEqualTo(new ReceiveServiceCaseOutcome.Received(number));
        ServiceCase saved = repository.cases.get(number.value());
        assertThat(saved.status()).isEqualTo(ServiceCaseStatus.IN_PROGRESS);
        assertThat(saved.assignee()).contains(actor.userId());
        assertThat(saved.receivedAt()).contains(new UtcInstant(MONDAY_10));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"SHIPPER", "SYSTEM_ADMIN", "AUDITOR"})
    void 行11_荷主担当者とシステム管理者と監査担当者は受領できない(Role role) {
        CaseNumber number = openedNumber();
        AuthenticatedActor actor = role == Role.SHIPPER ? shipper(SHIPPER_A) : staff(role);

        ReceiveServiceCaseOutcome outcome = service(MONDAY_10).receive(actor, new ReceiveServiceCaseCommand(number));

        assertThat(outcome).isEqualTo(new ReceiveServiceCaseOutcome.NotPermitted());
        assertThat(repository.cases.get(number.value()).status()).isEqualTo(ServiceCaseStatus.RECEIVED);
    }

    @Test
    void 行12_ない受付番号は見つからない() {
        ReceiveServiceCaseOutcome outcome = service(MONDAY_10)
                .receive(staff(Role.CUSTOMER_SUPPORT), new ReceiveServiceCaseCommand(new CaseNumber("SC-2099-9999")));

        assertThat(outcome).isEqualTo(new ReceiveServiceCaseOutcome.NotFound());
    }

    @Test
    void 行13_受領済みは受領できず状態を返す() {
        CaseNumber number = openedNumber();
        service(MONDAY_10).receive(staff(Role.CUSTOMER_SUPPORT), new ReceiveServiceCaseCommand(number));

        ReceiveServiceCaseOutcome outcome =
                service(MONDAY_10).receive(staff(Role.TRACKING_MANAGER), new ReceiveServiceCaseCommand(number));

        assertThat(outcome).isEqualTo(new ReceiveServiceCaseOutcome.NotReceivable(ServiceCaseStatus.IN_PROGRESS));
    }

    @Test
    void 行14_同時の更新に負けたら競合を返す() {
        CaseNumber number = openedNumber();
        repository.failNextUpdate = true;

        ReceiveServiceCaseOutcome outcome =
                service(MONDAY_10).receive(staff(Role.CUSTOMER_SUPPORT), new ReceiveServiceCaseCommand(number));

        assertThat(outcome).isEqualTo(new ReceiveServiceCaseOutcome.Conflict());
    }
}
