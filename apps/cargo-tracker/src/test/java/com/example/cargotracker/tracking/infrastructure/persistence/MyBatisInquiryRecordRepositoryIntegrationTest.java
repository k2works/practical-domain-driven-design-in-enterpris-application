package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 照会記録の保存（PostgreSQL。Bolt 40-15）。表の制約（経路・照会者の種別・有人対応へ移ったなら受付番号）も確かめる。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisInquiryRecordRepositoryIntegrationTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040f2001"));
    private static final UserId SUPPORT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040f2002"));

    @Autowired
    InquiryRecordRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private static InquiryRecord inquiryOf(
            UUID id,
            InquiryChannel channel,
            Optional<com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber> caseNumber) {
        return new InquiryRecord(
                id,
                channel,
                SHIPPER,
                Optional.of(new TrackingNumber("CTABCDEFGH2345")),
                InquirerKind.CONSIGNEE,
                Optional.empty(),
                new UtcInstant(Instant.parse("2026-11-16T00:30:00Z")),
                caseNumber,
                Optional.of(SUPPORT));
    }

    @Test
    void 引き継ぎなしの照会記録を保存して読み直すと同じ() {
        UUID id = UUID.randomUUID();

        repository.save(inquiryOf(id, InquiryChannel.PHONE, Optional.empty()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM tracking.inquiry_record WHERE id = ?", id);
        assertThat(row)
                .containsEntry("channel", "PHONE")
                .containsEntry("shipper_company_id", SHIPPER.value())
                .containsEntry("tracking_number", "CTABCDEFGH2345")
                .containsEntry("inquirer_kind", "CONSIGNEE")
                .containsEntry("escalated", false)
                .containsEntry("case_number", null)
                .containsEntry("recorded_by", SUPPORT.value());
    }

    @Test
    void 経路が範囲外の行は拒む() {
        assertThatThrownBy(() -> insertRaw("FAX", "SHIPPER", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 制約の確かめ用に、値をそのまま表へ入れる（受付番号なし）。 */
    private void insertRaw(String channel, String inquirerKind, boolean escalated) {
        jdbc.update(
                "INSERT INTO tracking.inquiry_record (id, channel, shipper_company_id, inquirer_kind, received_at, escalated, created_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                channel,
                SHIPPER.value(),
                inquirerKind,
                escalated);
    }

    @Test
    void 有人対応へ移ったのに受付番号がない行は拒む() {
        assertThatThrownBy(() -> insertRaw("PHONE", "SHIPPER", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 照会者の種別が範囲外の行は拒む() {
        assertThatThrownBy(() -> insertRaw("PHONE", "OTHER", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Bolt 42-1: 顧客 Web の自己照会のまとめの判定 ---

    private static final UserId SHIPPER_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a2001"));
    private static final TrackingNumber WEB_TRACKING = new TrackingNumber("CTABCDEFGH2346");
    private static final UtcInstant WEB_AT = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));

    private InquiryRecord webSelf(UserId user, InquiryChannel channel) {
        return new InquiryRecord(
                UUID.randomUUID(),
                channel,
                SHIPPER,
                Optional.of(WEB_TRACKING),
                InquirerKind.SHIPPER,
                Optional.of(user),
                WEB_AT,
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void 顧客Webの自己照会は指定の時刻より後のものだけを数える_ちょうどの時刻は含まない() {
        repository.save(webSelf(SHIPPER_USER, InquiryChannel.WEB_SELF));

        assertThat(repository.existsWebSelfAfter(
                        SHIPPER_USER,
                        WEB_TRACKING,
                        new UtcInstant(WEB_AT.instant().minusSeconds(1))))
                .isTrue();
        assertThat(repository.existsWebSelfAfter(SHIPPER_USER, WEB_TRACKING, WEB_AT))
                .isFalse();
    }

    @Test
    void 顧客Webの自己照会のまとめの判定は利用者と追跡番号と経路が同じものだけ() {
        repository.save(webSelf(SHIPPER_USER, InquiryChannel.WEB_SELF));
        UtcInstant before = new UtcInstant(WEB_AT.instant().minusSeconds(60));

        assertThat(repository.existsWebSelfAfter(
                        new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a2002")), WEB_TRACKING, before))
                .isFalse();
        assertThat(repository.existsWebSelfAfter(SHIPPER_USER, new TrackingNumber("CTABCDEFGH2347"), before))
                .isFalse();
        repository.save(webSelf(
                new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a2003")), InquiryChannel.WEB_INQUIRY));
        assertThat(repository.existsWebSelfAfter(
                        new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a2003")), WEB_TRACKING, before))
                .isFalse();
    }

    // --- W9 の Bolt 44: 24 時間の規則（有人対応へ移ったかの更新） ---

    @Autowired
    ServiceCaseRepository serviceCases;

    @Autowired
    ServiceCaseNumberIssuer numberIssuer;

    private com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber openCase() {
        Instant opened = Instant.parse("2026-11-16T09:00:00Z");
        com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber number = numberIssuer.next(2026);
        ServiceCase serviceCase = ((ServiceCaseOpening.Opened) ServiceCase.open(
                        ServiceCaseDraft.of(
                                        ServiceCaseKind.CUSTOMER_INQUIRY,
                                        ServicePriority.NORMAL,
                                        "更新の確かめ " + UUID.randomUUID(),
                                        "CS")
                                .withShipper(SHIPPER),
                        number,
                        new ServiceDeadlines(opened, opened.plusSeconds(14400), opened.plusSeconds(86400)),
                        new UtcInstant(opened)))
                .serviceCase();
        serviceCases.save(serviceCase, SUPPORT);
        return number;
    }

    @Test
    void 有人対応へ移っていない自己照会を範囲で探し_移った印と受付番号を付けると探索から外れる() {
        InquiryRecord inside = webSelf(SHIPPER_USER, InquiryChannel.WEB_SELF);
        repository.save(inside);
        repository.save(webSelf(SHIPPER_USER, InquiryChannel.WEB_INQUIRY));
        UtcInstant from = new UtcInstant(WEB_AT.instant().minusSeconds(60));
        UtcInstant to = new UtcInstant(WEB_AT.instant().plusSeconds(60));

        assertThat(repository.findUnescalatedWebSelf(SHIPPER, WEB_TRACKING, from, to))
                .singleElement()
                .satisfies(found -> assertThat(found.id()).isEqualTo(inside.id()));
        // 範囲は両端を含む。ちょうどの時刻は含み、1 秒外れると含まない
        assertThat(repository.findUnescalatedWebSelf(SHIPPER, WEB_TRACKING, WEB_AT, WEB_AT))
                .hasSize(1);
        assertThat(repository.findUnescalatedWebSelf(
                        SHIPPER, WEB_TRACKING, new UtcInstant(WEB_AT.instant().plusSeconds(1)), to))
                .isEmpty();

        var number = openCase();
        repository.escalate(inside.id(), number);

        assertThat(repository.findUnescalatedWebSelf(SHIPPER, WEB_TRACKING, from, to))
                .isEmpty();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM tracking.inquiry_record WHERE id = ?", inside.id());
        assertThat(row).containsEntry("escalated", true).containsEntry("case_number", number.value());
    }

    @Test
    void 期間の照会の件数は始まりを含み終わりを含まず_Webで完結した数は自己照会で移っていないものだけ() {
        UtcInstant start = new UtcInstant(WEB_AT.instant().minusSeconds(3600));
        UtcInstant end = new UtcInstant(WEB_AT.instant().plusSeconds(3600));
        InquiryRecord selfServed = webSelf(SHIPPER_USER, InquiryChannel.WEB_SELF);
        InquiryRecord moved = webSelf(SHIPPER_USER, InquiryChannel.WEB_SELF);
        repository.save(selfServed);
        repository.save(moved);
        repository.save(inquiryOf(UUID.randomUUID(), InquiryChannel.PHONE, Optional.empty()));
        repository.escalate(moved.id(), openCase());

        InquiryCountSummary summary = repository.countBetween(start, end);

        assertThat(summary.total()).isGreaterThanOrEqualTo(3);
        long before = repository.countBetween(start, end).selfServed();
        // 範囲は始まりを含み終わりを含まない
        assertThat(repository.countBetween(WEB_AT, WEB_AT).total()).isZero();
        assertThat(repository
                        .countBetween(WEB_AT, new UtcInstant(WEB_AT.instant().plusSeconds(1)))
                        .total())
                .isGreaterThanOrEqualTo(2);
        assertThat(before).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 追跡番号と受付時刻の索引がある() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'tracking' AND indexname = 'ix_inquiry_record_tracking_received'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void 受付番号の索引がある() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'tracking' AND indexname = 'ix_inquiry_record_case_number'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
