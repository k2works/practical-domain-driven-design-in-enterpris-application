package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentServiceCaseUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseClosing;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 有人案件の表（PostgreSQL）とリポジトリ・受付番号の採番（Bolt 40-6。計画の行 1〜6）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisServiceCaseRepositoryIntegrationTest {

    private static final UserId ACTOR = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c6001"));
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c6002"));
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c6003"));
    private static final UUID BOOKING = UUID.fromString("00000000-0000-0000-0000-0000040c6004");
    private static final TrackingNumber TRACKING = new TrackingNumber("CTABCDEFGH2345");
    private static final UtcInstant OPENED_AT = new UtcInstant(Instant.parse("2083-11-16T01:00:00.123456Z"));
    private static final ServiceDeadlines DEADLINES = new ServiceDeadlines(
            Instant.parse("2083-11-16T01:00:00Z"),
            Instant.parse("2083-11-16T05:00:00Z"),
            Instant.parse("2083-11-18T01:00:00Z"));

    @Autowired
    ServiceCaseRepository repository;

    @Autowired
    ServiceCaseNumberIssuer issuer;

    @Autowired
    JdbcTemplate jdbc;

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static ServiceCaseDraft draftOf(ServiceCaseKind kind) {
        Source source = new Source(SourceKind.INTERNAL_CHECK, "check-1", at("2083-11-16T00:00:00.654321Z"));
        return switch (kind) {
            case CUSTOMER_INQUIRY, QUOTATION_CONSULTATION ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "荷主の照会", "CS").withShipper(SHIPPER);
            case AMENDMENT, REDESIGN ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "変更の要求", "運用")
                        .withBooking(BOOKING)
                        .withTracking(TRACKING)
                        .withVersions("V1", "V2");
            case EXTERNAL_OUTAGE ->
                ServiceCaseDraft.of(kind, ServicePriority.HIGH, "外部停止", "運用").withManualSource("manual-1");
            case REJECTED_MILESTONE ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "拒否した実績", "運用")
                        .withTracking(TRACKING)
                        .withSources(List.of(source));
            case SAGA_FAILURE ->
                ServiceCaseDraft.of(kind, ServicePriority.HIGH, "予約サガの失敗", "運用").withBooking(BOOKING);
        };
    }

    private ServiceCase open(ServiceCaseKind kind) {
        CaseNumber number = issuer.next(2083);
        return ((ServiceCaseOpening.Opened) ServiceCase.open(draftOf(kind), number, DEADLINES, OPENED_AT))
                .serviceCase();
    }

    // --- 行 1: 採番 ---

    @Test
    void 行1_同じ年は1から連番で年が変われば1から() {
        CaseNumber first = issuer.next(2091);
        CaseNumber second = issuer.next(2091);
        CaseNumber otherYear = issuer.next(2092);

        assertThat(first.value()).isEqualTo("SC-2091-0001");
        assertThat(second.value()).isEqualTo("SC-2091-0002");
        assertThat(otherYear.value()).isEqualTo("SC-2092-0001");
    }

    // --- 行 2: 起票した案件の往復 ---

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServiceCaseKind.class)
    void 行2_起票した案件を保存して読み直すと同じ(ServiceCaseKind kind) {
        ServiceCase opened = open(kind);

        repository.save(opened, ACTOR);

        ServiceCase found = repository.findByNumber(opened.number()).orElseThrow();
        assertThat(found.number()).isEqualTo(opened.number());
        assertThat(found.kind()).isEqualTo(kind);
        assertThat(found.status()).isEqualTo(ServiceCaseStatus.RECEIVED);
        assertThat(found.cause()).isEqualTo(opened.cause());
        assertThat(found.department()).isEqualTo(opened.department());
        assertThat(found.priority()).isEqualTo(opened.priority());
        assertThat(found.shipperCompanyId()).isEqualTo(opened.shipperCompanyId());
        assertThat(found.bookingId()).isEqualTo(opened.bookingId());
        assertThat(found.trackingNumber()).isEqualTo(opened.trackingNumber());
        assertThat(found.sources()).isEqualTo(opened.sources());
        assertThat(found.oldVersionRef()).isEqualTo(opened.oldVersionRef());
        assertThat(found.newVersionRef()).isEqualTo(opened.newVersionRef());
        assertThat(found.manualSourceRef()).isEqualTo(opened.manualSourceRef());
        assertThat(found.reconciliationRequired()).isEqualTo(opened.reconciliationRequired());
        assertThat(found.receiveDueAt()).isEqualTo(opened.receiveDueAt());
        assertThat(found.answerDueAt()).isEqualTo(opened.answerDueAt());
        assertThat(found.nextUpdateAt()).isEqualTo(opened.nextUpdateAt());
        assertThat(found.openedAt()).isEqualTo(opened.openedAt());
        assertThat(found.acknowledgedAt()).isEqualTo(opened.acknowledgedAt());
        assertThat(found.aggregateVersion()).isZero();
        assertThat(found.progress()).isEmpty();
        assertThat(found.answers()).isEmpty();
    }

    // --- 行 3: 更新の往復 ---

    @Test
    void 行3_受領から再開までの更新を保存すると進捗と回答が追記され過去の分は変わらない() {
        ServiceCase opened = open(ServiceCaseKind.EXTERNAL_OUTAGE);
        repository.save(opened, ACTOR);

        ServiceCase received =
                repository.findByNumber(opened.number()).orElseThrow().receive(STAFF, at("2083-11-16T02:00:00Z"));
        repository.update(received, STAFF);

        ServiceCase progressed = repository
                .findByNumber(opened.number())
                .orElseThrow()
                .updateProgress("調査中", at("2083-11-17T01:00:00Z"), STAFF, at("2083-11-16T02:30:00Z"));
        repository.update(progressed, STAFF);

        ServiceCase answered = repository
                .findByNumber(opened.number())
                .orElseThrow()
                .recordAnswer("復旧しました", STAFF, at("2083-11-16T03:00:00Z"))
                .completeReconciliation(UUID.randomUUID());
        repository.update(answered, STAFF);

        ServiceCase closed = ((ServiceCaseClosing.Closed)
                        repository.findByNumber(opened.number()).orElseThrow().close())
                .serviceCase();
        repository.update(closed, STAFF);

        ServiceCase reopened =
                repository.findByNumber(opened.number()).orElseThrow().reopen("まだ直っていません", at("2083-11-17T00:00:00Z"));
        repository.update(reopened, STAFF);

        ServiceCase found = repository.findByNumber(opened.number()).orElseThrow();
        assertThat(found.status()).isEqualTo(ServiceCaseStatus.IN_PROGRESS);
        assertThat(found.assignee()).contains(STAFF);
        assertThat(found.receivedAt()).contains(at("2083-11-16T02:00:00Z"));
        assertThat(found.nextUpdateAt()).isEqualTo(at("2083-11-17T01:00:00Z"));
        assertThat(found.progress()).singleElement().satisfies(progress -> {
            assertThat(progress.progressNo()).isEqualTo(1);
            assertThat(progress.content()).isEqualTo("調査中");
        });
        assertThat(found.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.content()).isEqualTo("復旧しました");
            assertThat(answer.customerResponse()).contains("まだ直っていません");
            assertThat(answer.customerRespondedAt()).contains(at("2083-11-17T00:00:00Z"));
        });
        assertThat(found.reconciliationId()).isPresent();
        assertThat(found.aggregateVersion()).isEqualTo(5);
        // 受領・進捗・回答・終結・再開の 5 回の更新で、更新者が表に残る
        assertThat(jdbc.queryForObject(
                        "SELECT updated_by FROM tracking.service_case WHERE case_number = ?",
                        UUID.class,
                        opened.number().value()))
                .isEqualTo(STAFF.value());
        assertThat(jdbc.queryForObject(
                        "SELECT created_by FROM tracking.service_case WHERE case_number = ?",
                        UUID.class,
                        opened.number().value()))
                .isEqualTo(ACTOR.value());
    }

    // --- 行 4: 楽観ロック ---

    @Test
    void 行4_古い版で更新すると競合で表は変わらない() {
        ServiceCase opened = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(opened, ACTOR);
        ServiceCase first = opened.receive(STAFF, at("2083-11-16T02:00:00Z"));
        repository.update(first, STAFF);
        ServiceCase stale = opened.receive(ACTOR, at("2083-11-16T02:10:00Z"));

        assertThatThrownBy(() -> repository.update(stale, ACTOR))
                .isInstanceOf(ConcurrentServiceCaseUpdateException.class);

        ServiceCase found = repository.findByNumber(opened.number()).orElseThrow();
        assertThat(found.assignee()).contains(STAFF);
        assertThat(found.aggregateVersion()).isEqualTo(1);
    }

    // --- 行 5: CHECK の負例 ---

    private void insertCase(
            String number,
            String kind,
            String status,
            String priority,
            UUID assignee,
            UUID reconciliation,
            boolean required) {
        jdbc.update(
                "INSERT INTO tracking.service_case (case_number, kind, cause, priority, department, assignee_id,"
                        + " received_at, receive_due_at, reconciliation_required, reconciliation_id, status, version,"
                        + " created_at, created_by, updated_at, updated_by)"
                        + " VALUES (?, ?, 'c', ?, 'd', ?, ?, now(), ?, ?, ?, 0,"
                        + " now(), ?, now(), ?)",
                number,
                kind,
                priority,
                assignee,
                assignee == null ? null : java.time.OffsetDateTime.now(),
                required,
                reconciliation,
                status,
                ACTOR.value(),
                ACTOR.value());
    }

    @Test
    void 行5_種類が範囲外の行は拒む() {
        assertThatThrownBy(() -> insertCase("SC-X-1", "UNKNOWN", "RECEIVED", "NORMAL", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_状態が範囲外の行は拒む() {
        assertThatThrownBy(() -> insertCase("SC-X-2", "CUSTOMER_INQUIRY", "UNKNOWN", "NORMAL", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_優先度が範囲外の行は拒む() {
        assertThatThrownBy(() -> insertCase("SC-X-3", "CUSTOMER_INQUIRY", "RECEIVED", "LOW", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_担当者のいない対応中は拒む() {
        assertThatThrownBy(() -> insertCase("SC-X-4", "CUSTOMER_INQUIRY", "IN_PROGRESS", "NORMAL", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_復旧照合が必要で照合のない終結は拒む() {
        UUID staff = STAFF.value();

        assertThatThrownBy(() -> insertCase("SC-X-5", "EXTERNAL_OUTAGE", "CLOSED", "HIGH", staff, null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_復旧照合が要らないのに照合IDがある行は拒む() {
        UUID reconciliation = UUID.randomUUID();

        assertThatThrownBy(() ->
                        insertCase("SC-X-6", "CUSTOMER_INQUIRY", "RECEIVED", "NORMAL", null, reconciliation, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行5_顧客の回答だけある回答の行は拒む() {
        ServiceCase opened = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(opened, ACTOR);

        String number = opened.number().value();
        UUID staff = STAFF.value();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.service_case_answer (case_number, answer_no, content, answered_by,"
                                + " answered_at, customer_response) VALUES (?, 1, 'a', ?, now(), 'r')",
                        number,
                        staff))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- 重複の検索（Bolt 40-7） ---

    @Test
    void 重複_同じ荷主企業と予約と内容の未終結の案件の受付番号を返す() {
        ServiceCase opened = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(opened, ACTOR);

        assertThat(repository.findUnclosedDuplicate(Optional.of(SHIPPER), Optional.empty(), "荷主の照会"))
                .contains(opened.number());
    }

    @Test
    void 重複_荷主企業か予約か内容が違えば返さない() {
        ServiceCase opened = open(ServiceCaseKind.AMENDMENT);
        repository.save(opened, ACTOR);

        // 予約が同じ・荷主企業が空の案件
        assertThat(repository.findUnclosedDuplicate(Optional.empty(), Optional.of(BOOKING), "変更の要求"))
                .contains(opened.number());
        // 荷主企業を指すと、荷主企業のない案件とは別
        assertThat(repository.findUnclosedDuplicate(Optional.of(SHIPPER), Optional.of(BOOKING), "変更の要求"))
                .isEmpty();
        assertThat(repository.findUnclosedDuplicate(Optional.empty(), Optional.of(UUID.randomUUID()), "変更の要求"))
                .isEmpty();
        assertThat(repository.findUnclosedDuplicate(Optional.empty(), Optional.of(BOOKING), "別の内容"))
                .isEmpty();
    }

    @Test
    void 重複_終結した案件と重複終了した案件は返さない() {
        ServiceCase opened = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(opened, ACTOR);
        repository.update(opened.markDuplicate(), ACTOR);

        assertThat(repository.findUnclosedDuplicate(Optional.of(SHIPPER), Optional.empty(), "荷主の照会"))
                .isEmpty();
    }

    // --- 受領期限を過ぎた案件（Bolt 40-9） ---

    @Test
    void 期限超過_受付済みで期限を厳密に過ぎたものと上位対応中を期限の古い順に返す() {
        ServiceCase late = open(ServiceCaseKind.CUSTOMER_INQUIRY); // 受領期限 2083-11-16T05:00Z
        repository.save(late, ACTOR);
        ServiceCase received = open(ServiceCaseKind.SAGA_FAILURE);
        repository.save(received, ACTOR);
        repository.update(received.receive(STAFF, at("2083-11-16T02:00:00Z")), STAFF);

        // 共有の DB には、ほかのテストが残した案件（拒否した主要実績の起票など。Bolt 40-13）があるので、このテストの案件だけを比べる
        // 期限ちょうどは返さない
        assertThat(mine(repository.findReceiptOverdue(at("2083-11-16T05:00:00Z"), 100_000), late, received))
                .isEmpty();
        // 期限を過ぎた受付済みだけ返す（受領した案件は返さない）
        assertThat(mine(repository.findReceiptOverdue(at("2083-11-16T05:00:01Z"), 100_000), late, received))
                .containsExactly(late.number());
        // 上位対応中は期限の前後を問わず返す
        repository.update(late.escalate(at("2083-11-16T06:00:00Z")).orElseThrow(), ACTOR);
        assertThat(mine(repository.findReceiptOverdue(at("2083-11-16T04:00:00Z"), 100_000), late, received))
                .containsExactly(late.number());
    }

    /** 結果のうち、このテストで作った案件の受付番号だけを返す（共有の DB のほかの案件に影響されない）。 */
    private static List<com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber> mine(
            List<ServiceCase> found, ServiceCase... own) {
        return found.stream()
                .map(ServiceCase::number)
                .filter(number ->
                        java.util.Arrays.stream(own).anyMatch(c -> c.number().equals(number)))
                .toList();
    }

    // --- 起票元キー（Bolt 40-17。システム起票の冪等。週全体レビュー A） ---

    @Test
    void 起票元キー_記録して引け_同じキーを別の案件に記録すると一意制約で拒む() {
        ServiceCase first = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(first, ACTOR);
        ServiceCase second = open(ServiceCaseKind.SAGA_FAILURE);
        repository.save(second, ACTOR);
        String originRef = "ORIGIN-" + UUID.randomUUID();

        repository.recordOrigin(first.number(), originRef);

        assertThat(repository.findByOriginRef(originRef)).contains(first.number());
        assertThat(repository.findByOriginRef("ORIGIN-" + UUID.randomUUID())).isEmpty();
        CaseNumber secondNumber = second.number();
        assertThatThrownBy(() -> repository.recordOrigin(secondNumber, originRef))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void 起票元キー_利用者の起票は起票元キーが空で何件あっても重ならない() {
        repository.save(open(ServiceCaseKind.CUSTOMER_INQUIRY), ACTOR);
        repository.save(open(ServiceCaseKind.SAGA_FAILURE), ACTOR);

        assertThat(repository.findByOriginRef("存在しないキー")).isEmpty();
    }

    // --- 状態の集合で引く（Bolt 40-11。S-14 の未終結・未受領・対応中） ---

    @Test
    void 状態の集合_指定した状態の案件だけを受領期限の古い順に上限まで返す() {
        ServiceCase unreceived = open(ServiceCaseKind.CUSTOMER_INQUIRY);
        repository.save(unreceived, ACTOR);
        ServiceCase received = open(ServiceCaseKind.SAGA_FAILURE);
        repository.save(received, ACTOR);
        repository.update(received.receive(STAFF, at("2083-11-16T02:00:00Z")), STAFF);

        // 共有の DB のほかの案件に影響されないよう、このテストの案件だけを比べる
        assertThat(mine(repository.findByStatuses(Set.of(ServiceCaseStatus.RECEIVED), 100_000), unreceived, received))
                .containsExactly(unreceived.number());
        assertThat(mine(
                        repository.findByStatuses(Set.of(ServiceCaseStatus.IN_PROGRESS), 100_000),
                        unreceived,
                        received))
                .containsExactly(received.number());
        assertThat(mine(
                        repository.findByStatuses(
                                Set.of(ServiceCaseStatus.RECEIVED, ServiceCaseStatus.IN_PROGRESS), 100_000),
                        unreceived,
                        received))
                .hasSize(2);
        assertThat(repository.findByStatuses(Set.of(ServiceCaseStatus.RECEIVED, ServiceCaseStatus.IN_PROGRESS), 1))
                .hasSize(1);
        assertThat(repository.findByStatuses(Set.of(), 10)).isEmpty();
    }

    // --- Bolt 42-2: 荷主企業の問い合わせ ---

    @Test
    void 行7_荷主企業の案件だけを起票の新しい順に返し上限で打ち切る() {
        CompanyId company = new CompanyId(UUID.randomUUID());
        ServiceCase older = openFor(company, "古い照会", "2083-11-16T01:00:00Z");
        ServiceCase newer = openFor(company, "新しい照会", "2083-11-16T02:00:00Z");
        openFor(new CompanyId(UUID.randomUUID()), "他社の照会", "2083-11-16T03:00:00Z");
        repository.save(older, ACTOR);
        repository.save(newer, ACTOR);

        assertThat(repository.findByShipper(company, 10))
                .extracting(ServiceCase::number)
                .containsExactly(newer.number(), older.number());
        assertThat(repository.findByShipper(company, 1))
                .extracting(ServiceCase::number)
                .containsExactly(newer.number());
        assertThat(repository.findByShipper(new CompanyId(UUID.randomUUID()), 10))
                .isEmpty();
    }

    private ServiceCase openFor(CompanyId company, String cause, String openedAt) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, cause, "CS")
                .withShipper(company);
        CaseNumber number = issuer.next(2083);
        ServiceCase opened =
                ((ServiceCaseOpening.Opened) ServiceCase.open(draft, number, DEADLINES, at(openedAt))).serviceCase();
        // 保存するのは呼び手（他社の分は保存しない）
        return opened;
    }

    // --- 行 6: ない受付番号 ---

    @Test
    void 行6_ない受付番号は空を返す() {
        assertThat(repository.findByNumber(new CaseNumber("SC-2099-9999"))).isEmpty();
    }
}
