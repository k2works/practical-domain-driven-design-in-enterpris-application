package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求のリポジトリを PostgreSQL 18 で確かめる（ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisTransportRequestRepositoryIntegrationTest {

    private static TransportRequest submitted(TransportRequestNumber number) {
        return submitted(number, new CompanyId(UUID.randomUUID()));
    }

    private static TransportRequest submitted(TransportRequestNumber number, CompanyId shipper) {
        return TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                number,
                shipper,
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
    }

    @Autowired
    TransportRequestRepository repository;

    @Test
    void 提出した輸送要求を業務番号と輸送条件とともに保存して読み出せる() {
        TransportRequestId id = new TransportRequestId(UUID.randomUUID());
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        UserId submitter = new UserId(UUID.randomUUID());
        TransportRequestNumber number = new TransportRequestNumber(2088, 1);
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
        UtcInstant submittedAt = new UtcInstant(Instant.parse("2026-10-05T01:00:00.123456Z"));

        repository.save(TransportRequest.submit(id, number, shipper, terms, submitter, submittedAt));

        assertThat(repository.findById(id)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(id);
            assertThat(found.number()).isEqualTo(number);
            assertThat(found.shipperCompanyId()).isEqualTo(shipper);
            assertThat(found.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
            assertThat(found.currentVersion().versionNo()).isEqualTo(1);
            assertThat(found.currentVersion().terms()).isEqualTo(terms);
            assertThat(found.currentVersion().submittedBy()).isEqualTo(submitter);
            assertThat(found.currentVersion().submittedAt()).isEqualTo(submittedAt);
            assertThat(found.domainEvents()).isEmpty();
        });
    }

    @Test
    void 同じ業務番号の輸送要求は保存できない() {
        TransportRequestNumber number = new TransportRequestNumber(2096, 1);
        repository.save(submitted(number));
        TransportRequest sameNumber = submitted(number);

        assertThatThrownBy(() -> repository.save(sameNumber)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 荷主企業の輸送要求を業務番号で読み出せる() {
        TransportRequestNumber number = new TransportRequestNumber(2097, 1);
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        TransportRequest saved = submitted(number, shipper);
        repository.save(saved);

        assertThat(repository.findByNumber(number, shipper))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(saved.id()));
        assertThat(repository.findByNumber(new TransportRequestNumber(2097, 2), shipper))
                .isEmpty();
    }

    @Test
    void 他社の輸送要求は業務番号を知っていても見つからない() {
        TransportRequestNumber number = new TransportRequestNumber(2097, 3);
        repository.save(submitted(number, new CompanyId(UUID.randomUUID())));

        assertThat(repository.findByNumber(number, new CompanyId(UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void 存在しない輸送要求は見つからない() {
        assertThat(repository.findById(new TransportRequestId(UUID.randomUUID())))
                .isEmpty();
    }
}
