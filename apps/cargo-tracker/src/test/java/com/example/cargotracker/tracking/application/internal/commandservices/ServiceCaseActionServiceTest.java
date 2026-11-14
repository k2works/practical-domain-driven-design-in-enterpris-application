package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.CloseServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordServiceCaseAnswerCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReviseServiceCaseAnswerDueCommand;
import com.example.cargotracker.tracking.application.internal.commands.UpdateServiceCaseProgressCommand;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 有人案件の進捗・回答・終結・再開・回答期限の変更（Bolt 40-8。US-20 AC4〜AC7。認可の仮の定めは台帳 D-159）。 */
class ServiceCaseActionServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final CompanyId OPERATOR_COMPANY =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c8001"));
    private static final CompanyId SHIPPER_A = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c8002"));
    private static final CompanyId SHIPPER_B = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c8003"));
    private static final Instant MONDAY_10 = Instant.parse("2026-11-16T01:00:00Z");

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();
    private final ServiceCaseActionService actions =
            new ServiceCaseActionService(repository, Clock.fixed(MONDAY_10, ZoneOffset.UTC));

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
                    weekend
                            ? BusinessDay.closed(date, JST, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
        }
        return BusinessCalendar.of(days);
    }

    /** 受領済み（対応中）の顧客照会を用意する。 */
    private int sequence;

    private CaseNumber inProgressInquiry(CompanyId company) {
        ServiceCaseCommandService commands = new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(MONDAY_10, ZoneOffset.UTC));
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "荷主の照会 " + (++sequence), "CS")
                .withShipper(company);
        CaseNumber number = ((OpenServiceCaseOutcome.Opened) commands.open(
                        staff(Role.CUSTOMER_SUPPORT),
                        new com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand(
                                draft)))
                .number();
        commands.receive(
                staff(Role.CUSTOMER_SUPPORT),
                new com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand(number));
        return number;
    }

    private CaseNumber answeredOutage() {
        ServiceCaseCommandService commands = new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(MONDAY_10, ZoneOffset.UTC));
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "外部停止", "運用")
                .withManualSource("manual-1");
        CaseNumber number = ((OpenServiceCaseOutcome.Opened) commands.open(
                        staff(Role.DATA_STEWARD),
                        new com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand(
                                draft)))
                .number();
        commands.receive(
                staff(Role.DATA_STEWARD),
                new com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand(number));
        actions.recordAnswer(staff(Role.DATA_STEWARD), new RecordServiceCaseAnswerCommand(number, "手動で対応した"));
        return number;
    }

    // --- 進捗 ---

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"CUSTOMER_SUPPORT", "SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER", "DATA_STEWARD"})
    void 行1_社内の運用役割は進捗を更新できる(Role role) {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        UtcInstant next = new UtcInstant(Instant.parse("2026-11-17T01:00:00Z"));

        ServiceCaseActionOutcome outcome =
                actions.updateProgress(staff(role), new UpdateServiceCaseProgressCommand(number, "調査中", next));

        assertThat(outcome).isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.IN_PROGRESS));
        ServiceCase saved = repository.cases.get(number.value());
        assertThat(saved.progress())
                .singleElement()
                .satisfies(progress -> assertThat(progress.content()).isEqualTo("調査中"));
        assertThat(saved.nextUpdateAt()).isEqualTo(next);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"SHIPPER", "SYSTEM_ADMIN", "AUDITOR"})
    void 行2_荷主担当者とシステム管理者と監査担当者は進捗の更新と回答と終結と期限の変更ができない(Role role) {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        AuthenticatedActor actor = role == Role.SHIPPER ? shipper(SHIPPER_A) : staff(role);
        UtcInstant later = new UtcInstant(Instant.parse("2026-11-30T01:00:00Z"));
        ServiceCaseActionOutcome notPermitted = new ServiceCaseActionOutcome.NotPermitted();

        assertThat(actions.updateProgress(actor, new UpdateServiceCaseProgressCommand(number, "x", null)))
                .isEqualTo(notPermitted);
        assertThat(actions.recordAnswer(actor, new RecordServiceCaseAnswerCommand(number, "x")))
                .isEqualTo(notPermitted);
        assertThat(actions.close(actor, new CloseServiceCaseCommand(number))).isEqualTo(notPermitted);
        assertThat(actions.reviseAnswerDue(actor, new ReviseServiceCaseAnswerDueCommand(number, "理由", later)))
                .isEqualTo(notPermitted);
        assertThat(repository.cases.get(number.value()).progress()).isEmpty();
    }

    @Test
    void 行3_ない受付番号は見つからない() {
        assertThat(actions.updateProgress(
                        staff(Role.CUSTOMER_SUPPORT),
                        new UpdateServiceCaseProgressCommand(new CaseNumber("SC-2099-9999"), "x", null)))
                .isEqualTo(new ServiceCaseActionOutcome.NotFound());
    }

    @Test
    void 行4_対応中でなければ状態を返し内容が空なら入力の誤りを返す() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        AuthenticatedActor support = staff(Role.CUSTOMER_SUPPORT);

        assertThat(actions.updateProgress(support, new UpdateServiceCaseProgressCommand(number, " ", null)))
                .isInstanceOf(ServiceCaseActionOutcome.InvalidInput.class);
        actions.recordAnswer(support, new RecordServiceCaseAnswerCommand(number, "回答"));
        assertThat(actions.updateProgress(support, new UpdateServiceCaseProgressCommand(number, "x", null)))
                .isEqualTo(new ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus.ANSWERED));
    }

    // --- 回答と終結 ---

    @Test
    void 行5_回答を記録して終結できる() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        AuthenticatedActor support = staff(Role.CUSTOMER_SUPPORT);

        assertThat(actions.recordAnswer(support, new RecordServiceCaseAnswerCommand(number, "回答します")))
                .isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.ANSWERED));
        assertThat(actions.close(support, new CloseServiceCaseCommand(number)))
                .isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.CLOSED));
        assertThat(repository.cases.get(number.value()).answers()).hasSize(1);
    }

    @Test
    void 行6_復旧照合が未完了の終結は拒否して不足する照合結果を返し状態は回答済みのまま() {
        CaseNumber number = answeredOutage();

        ServiceCaseActionOutcome outcome = actions.close(staff(Role.DATA_STEWARD), new CloseServiceCaseCommand(number));

        assertThat(outcome)
                .isEqualTo(new ServiceCaseActionOutcome.ReconciliationIncomplete(
                        Set.of(MissingItem.RECONCILIATION_RESULT)));
        assertThat(repository.cases.get(number.value()).status()).isEqualTo(ServiceCaseStatus.ANSWERED);
    }

    @Test
    void 行7_回答済みでなければ終結できない() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);

        assertThat(actions.close(staff(Role.CUSTOMER_SUPPORT), new CloseServiceCaseCommand(number)))
                .isEqualTo(new ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus.IN_PROGRESS));
    }

    // --- 再開 ---

    private CaseNumber closedInquiry(CompanyId company) {
        CaseNumber number = inProgressInquiry(company);
        AuthenticatedActor support = staff(Role.CUSTOMER_SUPPORT);
        actions.recordAnswer(support, new RecordServiceCaseAnswerCommand(number, "回答します"));
        actions.close(support, new CloseServiceCaseCommand(number));
        return number;
    }

    @Test
    void 行8_社内の運用役割は再開でき受付番号と過去の回答を保持する() {
        CaseNumber number = closedInquiry(SHIPPER_A);

        ServiceCaseActionOutcome outcome =
                actions.reopen(staff(Role.CUSTOMER_SUPPORT), new ReopenServiceCaseCommand(number, "電話で未解決と聞いた"));

        assertThat(outcome).isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.IN_PROGRESS));
        ServiceCase saved = repository.cases.get(number.value());
        assertThat(saved.number()).isEqualTo(number);
        assertThat(saved.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.content()).isEqualTo("回答します");
            assertThat(answer.customerResponse()).contains("電話で未解決と聞いた");
        });
    }

    @Test
    void 行9_荷主担当者は自社の案件だけ再開でき他社の案件は見つからない() {
        CaseNumber number = closedInquiry(SHIPPER_A);

        assertThat(actions.reopen(shipper(SHIPPER_B), new ReopenServiceCaseCommand(number, "未解決")))
                .isEqualTo(new ServiceCaseActionOutcome.NotFound());
        assertThat(repository.cases.get(number.value()).status()).isEqualTo(ServiceCaseStatus.CLOSED);

        assertThat(actions.reopen(shipper(SHIPPER_A), new ReopenServiceCaseCommand(number, "未解決")))
                .isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.IN_PROGRESS));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Role.class,
            names = {"SYSTEM_ADMIN", "AUDITOR"})
    void 行10_システム管理者と監査担当者は再開できない(Role role) {
        CaseNumber number = closedInquiry(SHIPPER_A);

        assertThat(actions.reopen(staff(role), new ReopenServiceCaseCommand(number, "未解決")))
                .isEqualTo(new ServiceCaseActionOutcome.NotPermitted());
    }

    @Test
    void 行11_終結でなければ再開できず顧客の回答が空なら入力の誤り() {
        CaseNumber open = inProgressInquiry(SHIPPER_A);
        CaseNumber closed = closedInquiry(SHIPPER_A);
        AuthenticatedActor support = staff(Role.CUSTOMER_SUPPORT);

        assertThat(actions.reopen(support, new ReopenServiceCaseCommand(open, "未解決")))
                .isEqualTo(new ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus.IN_PROGRESS));
        assertThat(actions.reopen(support, new ReopenServiceCaseCommand(closed, " ")))
                .isInstanceOf(ServiceCaseActionOutcome.InvalidInput.class);
    }

    // --- 回答期限の変更 ---

    @Test
    void 行12_回答期限を理由つきで後ろへ変更できる() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        UtcInstant newDue = new UtcInstant(Instant.parse("2026-11-30T01:00:00Z"));

        ServiceCaseActionOutcome outcome = actions.reviseAnswerDue(
                staff(Role.TRACKING_MANAGER), new ReviseServiceCaseAnswerDueCommand(number, "調査に時間が要る", newDue));

        assertThat(outcome).isEqualTo(new ServiceCaseActionOutcome.Done(number, ServiceCaseStatus.IN_PROGRESS));
        assertThat(repository.cases.get(number.value()).answerDueAt()).isEqualTo(newDue);
    }

    @Test
    void 行13_理由が空か期限が後でなければ入力の誤り() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        AuthenticatedActor support = staff(Role.CUSTOMER_SUPPORT);
        UtcInstant later = new UtcInstant(Instant.parse("2026-11-30T01:00:00Z"));
        UtcInstant earlier = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));

        assertThat(actions.reviseAnswerDue(support, new ReviseServiceCaseAnswerDueCommand(number, " ", later)))
                .isInstanceOf(ServiceCaseActionOutcome.InvalidInput.class);
        assertThat(actions.reviseAnswerDue(support, new ReviseServiceCaseAnswerDueCommand(number, "理由", earlier)))
                .isInstanceOf(ServiceCaseActionOutcome.InvalidInput.class);
    }

    // --- 競合 ---

    @Test
    void 行14_同時の更新に負けたら競合を返す() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        repository.failNextUpdate = true;

        assertThat(actions.recordAnswer(staff(Role.CUSTOMER_SUPPORT), new RecordServiceCaseAnswerCommand(number, "回答")))
                .isEqualTo(new ServiceCaseActionOutcome.Conflict());
    }

    // --- 業務上の拒否とバグの区別（Bolt 41） ---

    @Test
    void 行15_業務上の拒否ではない例外はそのまま失敗させ状態の違いに見せかけない() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);
        Clock broken = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                throw new IllegalStateException("時計の故障");
            }
        };
        ServiceCaseActionService brokenClock = new ServiceCaseActionService(repository, broken);

        assertThatThrownBy(() -> brokenClock.recordAnswer(
                        staff(Role.CUSTOMER_SUPPORT), new RecordServiceCaseAnswerCommand(number, "回答")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("時計の故障");
    }

    @Test
    void 行16_回答の内容が空なら入力の誤りとして返す() {
        CaseNumber number = inProgressInquiry(SHIPPER_A);

        assertThat(actions.recordAnswer(staff(Role.CUSTOMER_SUPPORT), new RecordServiceCaseAnswerCommand(number, " ")))
                .isInstanceOf(ServiceCaseActionOutcome.InvalidInput.class);
    }
}
