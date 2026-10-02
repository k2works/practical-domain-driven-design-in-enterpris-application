package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * スモーク（H2）: ローカル起動と同じ dev プロファイルで、アプリケーションが起動しマイグレーションが通る（ADR-007、テスト戦略）。
 * H2 でも動く必要のある SQL（KPI 計測記録の冪等な保存）は、リポジトリの契約で確かめる。
 * 業務番号の採番と、提出した輸送要求の保存も H2 で動くことを確かめる（同時実行は PostgreSQL の統合テストで確かめる）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class H2DevProfileSmokeTest extends KpiObservationRepositoryContract {

    @Autowired
    Flyway flyway;

    @Autowired
    KpiObservationRepository repository;

    @Autowired
    TransportRequestNumberIssuer numberIssuer;

    @Autowired
    TransportRequestCommandService commandService;

    @Autowired
    TransportRequestRepository transportRequestRepository;

    @Override
    protected KpiObservationRepository repository() {
        return repository;
    }

    @Test
    void H2で起動しマイグレーションがすべて適用される() {
        assertThat(flyway.getConfiguration().getDataSource()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();
    }

    @Test
    void H2で年ごとに業務番号を振れる() {
        assertThat(numberIssuer.next(2089)).isEqualTo(new TransportRequestNumber(2089, 1));
        assertThat(numberIssuer.next(2089)).isEqualTo(new TransportRequestNumber(2089, 2));
    }

    @Test
    void H2で提出した輸送要求を業務番号で読み出せる() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        SubmissionOutcome outcome = commandService.submit(new SubmitTransportRequestCommand(
                shipper, new UserId(UUID.randomUUID()), ShipmentTermsFixture.completeInput()));

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        SubmissionOutcome.Submitted.class,
                        submitted -> assertThat(transportRequestRepository.findByNumber(submitted.number(), shipper))
                                .hasValueSatisfying(found -> assertThat(
                                                found.currentVersion().terms())
                                        .isEqualTo(ShipmentTermsFixture.generalCargo())));
    }
}
