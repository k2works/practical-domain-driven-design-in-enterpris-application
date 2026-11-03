package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadiness;
import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadinessQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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

    @Autowired
    UserRepository users;

    @Autowired
    PilotReadinessQueryService pilotReadiness;

    @Autowired
    DataSourceRepository dataSources;

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

    @Test
    void devでは開発用の荷主2人と営業1人と追跡管理者1人が入っている() {
        assertThat(users.findByEmail(EmailAddress.of("shipper@dev.cargo-tracker.example")))
                .hasValueSatisfying(user -> {
                    assertThat(user.id().value()).hasToString("00000000-0000-0000-0000-000000000101");
                    assertThat(user.companyId().value()).hasToString("00000000-0000-0000-0000-000000000001");
                    assertThat(user.roles()).containsExactly(Role.SHIPPER);
                });
        assertThat(users.findByEmail(EmailAddress.of("shipper-b@dev.cargo-tracker.example")))
                .hasValueSatisfying(user ->
                        assertThat(user.companyId().value()).hasToString("00000000-0000-0000-0000-000000000002"));
        assertThat(users.findByEmail(EmailAddress.of("sales@dev.cargo-tracker.example")))
                .hasValueSatisfying(user -> {
                    assertThat(user.id().value()).hasToString("00000000-0000-0000-0000-000000000301");
                    assertThat(user.roles()).containsExactly(Role.SALES);
                });
        assertThat(users.findByEmail(EmailAddress.of("tracking-manager@dev.cargo-tracker.example")))
                .hasValueSatisfying(user -> {
                    assertThat(user.id().value()).hasToString("00000000-0000-0000-0000-000000000303");
                    assertThat(user.companyId().value()).hasToString("00000000-0000-0000-0000-000000000003");
                    assertThat(user.roles()).containsExactly(Role.TRACKING_MANAGER);
                });
    }

    /** Bolt 39b の行 14。開発データの承認済みの情報源 2 つと候補 1 つ、データ責任者の開発利用者（確認ポイント 9）。 */
    @Test
    void 行14_devでは開発用のデータ責任者と承認済みの情報源2つが入りパイロットを開始できる() {
        assertThat(users.findByEmail(EmailAddress.of("data-steward@dev.cargo-tracker.example")))
                .hasValueSatisfying(user -> {
                    assertThat(user.id().value()).hasToString("00000000-0000-0000-0000-000000000307");
                    assertThat(user.companyId().value()).hasToString("00000000-0000-0000-0000-000000000003");
                    assertThat(user.roles()).containsExactly(Role.DATA_STEWARD);
                });
        assertThat(dataSources.findById(UUID.fromString("00000000-0000-0000-0000-000000003901")))
                .hasValueSatisfying(source -> {
                    assertThat(source.recordType()).isEqualTo("VOYAGE");
                    assertThat(source.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
                });
        assertThat(dataSources.findById(UUID.fromString("00000000-0000-0000-0000-000000003902")))
                .hasValueSatisfying(source -> {
                    assertThat(source.recordType()).isEqualTo("MILESTONE");
                    assertThat(source.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
                });
        assertThat(dataSources.findById(UUID.fromString("00000000-0000-0000-0000-000000003903")))
                .hasValueSatisfying(
                        source -> assertThat(source.approvalStatus()).isEqualTo(ApprovalStatus.CANDIDATE));

        PilotReadiness readiness = pilotReadiness.readiness();

        assertThat(Stream.of(
                                "00000000-0000-0000-0000-000000003901",
                                "00000000-0000-0000-0000-000000003902",
                                "00000000-0000-0000-0000-000000003903")
                        .map(UUID::fromString)
                        .map(dataSources::findById)
                        .flatMap(Optional::stream)
                        .filter(source -> source.approvalStatus() == ApprovalStatus.APPROVED))
                .hasSize(2);
        // 共有の H2 にほかのテストの承認済みの情報源が残っても崩れないよう、全体の件数は開発データの 2 つ以上で確かめる。
        assertThat(readiness.approvedCount()).isGreaterThanOrEqualTo(2);
        assertThat(readiness.canStart()).isTrue();
    }
}
