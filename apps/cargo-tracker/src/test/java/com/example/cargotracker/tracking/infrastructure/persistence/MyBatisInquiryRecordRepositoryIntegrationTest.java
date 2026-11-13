package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
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

    private static InquiryRecord record(
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

        repository.save(record(id, InquiryChannel.PHONE, Optional.empty()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM tracking.inquiry_record WHERE id = ?", id);
        assertThat(row.get("channel")).isEqualTo("PHONE");
        assertThat(row.get("shipper_company_id")).isEqualTo(SHIPPER.value());
        assertThat(row.get("tracking_number")).isEqualTo("CTABCDEFGH2345");
        assertThat(row.get("inquirer_kind")).isEqualTo("CONSIGNEE");
        assertThat(row.get("escalated")).isEqualTo(false);
        assertThat(row.get("case_number")).isNull();
        assertThat(row.get("recorded_by")).isEqualTo(SUPPORT.value());
    }

    @Test
    void 経路が範囲外の行は拒む() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.inquiry_record (id, channel, shipper_company_id, inquirer_kind, received_at, escalated, created_at)"
                                + " VALUES (?, 'FAX', ?, 'SHIPPER', CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(),
                        SHIPPER.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 有人対応へ移ったのに受付番号がない行は拒む() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.inquiry_record (id, channel, shipper_company_id, inquirer_kind, received_at, escalated, created_at)"
                                + " VALUES (?, 'PHONE', ?, 'SHIPPER', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(),
                        SHIPPER.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 照会者の種別が範囲外の行は拒む() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.inquiry_record (id, channel, shipper_company_id, inquirer_kind, received_at, escalated, created_at)"
                                + " VALUES (?, 'PHONE', ?, 'OTHER', CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(),
                        SHIPPER.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
