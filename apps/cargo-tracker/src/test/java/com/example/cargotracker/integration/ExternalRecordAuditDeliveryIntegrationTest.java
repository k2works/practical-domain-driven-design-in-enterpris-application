package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部原本の採否の監査の配信を PostgreSQL 18 で確かめる（Bolt 39b3b の行 5・6・8・9・10・15。T-184、ADR-003、IA-INV-15）。取込の
 * コミットの後に監査の listener が DE-12・DE-13・DE-14 を非同期に受け、受信記録ごとに監査記録を 1 つ書き、監査の listener の
 * イベント発行記録が完了になる。巻き戻った取込では listener が呼ばれない。同じイベントが再び届いても監査記録は増えない。
 *
 * <p>配信を確かめるので取込のサービス自身のトランザクションをコミットする。監査記録・取込・イベント発行記録は共有の DB に残る（監査記録と
 * 取込は追記専用）。確かめる行は自分の受信記録 ID と、UUID を含む提供元の名前の情報源で絞り、情報源は最後に停止中にして承認済みのまま
 * 残さない。起きないことは監査記録のリポジトリの呼び出しの記録で確かめる（T-236）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalRecordAuditDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";

    /** 寄港の時刻の始まり（寄港ごとに 1 日ずらす）。 */
    private static final Instant FIRST_CALL = Instant.parse("2026-12-01T00:00:00Z");

    private static final String AUDIT_LISTENER = ExternalRecordAuditEventHandler.class.getSimpleName();

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    @Autowired
    IngestionService ingestionService;

    @MockitoSpyBean
    AuditRecordRepository auditRecords;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Autowired
    IncompleteEventPublications incompleteEventPublications;

    @Autowired
    EventPublicationRepository eventPublicationRepository;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void 行8_取込のコミットの後に監査のlistenerが受信記録ごとに監査記録を書き発行記録が完了になる() {
        UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
        try {
            Ingested result = ingested(ingestionService.ingest(
                    steward,
                    milestoneFile(
                            sourceId,
                            milestone(2, "MS-0001", "CTABCDEFGH2345"),
                            milestone(3, "MS-0001", "CTABCDEFGH2346"))));
            assertThat(result.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
            UUID adopted = result.rows().get(0).receivedRecordId();
            UUID quarantined = result.rows().get(1).receivedRecordId();

            awaitCompletedAuditPublication(MilestoneAdoptedValueAccepted.class, adopted);
            awaitCompletedAuditPublication(ExternalRecordQuarantined.class, quarantined);

            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, adopted))
                    .containsEntry("result", "SUCCESS")
                    .containsEntry("actor_user_id", steward.userId().value())
                    .containsEntry("reason", null);
            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_QUARANTINED, quarantined))
                    .containsEntry("result", "SUCCESS")
                    .containsEntry("actor_user_id", steward.userId().value())
                    .containsEntry("reason", ExternalRecordQuarantined.CONTENT_MISMATCH);
            // 行 15: 発生時刻はイベントの時刻（取込の時刻）で、listener の時刻ではない
            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, adopted))
                    .containsEntry("occurred_at", ingestedAt(result.ingestionId()));
            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_QUARANTINED, quarantined))
                    .containsEntry("occurred_at", ingestedAt(result.ingestionId()));
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行10_寄港60の航海のDE12がPostgreSQLの発行記録に入り取込がコミットされ監査記録ができる() {
        UUID sourceId = insertApprovedSource("船会社-" + UUID.randomUUID(), "VOYAGE");
        try {
            Ingested result = ingested(ingestionService.ingest(steward, voyageFile(sourceId)));
            assertThat(result.rows()).extracting(RowResult::classification).containsExactly(Classification.ADOPTED);
            UUID receivedRecordId = result.rows().getFirst().receivedRecordId();

            awaitCompletedAuditPublication(VoyageAdoptedValueUpdated.class, receivedRecordId);

            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, receivedRecordId))
                    .containsEntry("occurred_at", ingestedAt(result.ingestionId()));
            assertThat(count("SELECT COUNT(*) FROM external_data.ingestion WHERE id = ?", result.ingestionId()))
                    .isEqualTo(1);
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行5と行15_同じイベントを2つのトランザクションで続けて配り直しても監査記録は増えず発生時刻も変わらない() {
        UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
        try {
            Ingested result = ingested(ingestionService.ingest(
                    steward, milestoneFile(sourceId, milestone(2, "MS-0001", "CTABCDEFGH2345"))));
            UUID receivedRecordId = result.rows().getFirst().receivedRecordId();
            awaitCompletedAuditPublication(MilestoneAdoptedValueAccepted.class, receivedRecordId);
            Object occurredAt = auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, receivedRecordId)
                    .get("occurred_at");
            MilestoneAdoptedValueAccepted delivered = completedEventPublications.findAll().stream()
                    .map(EventPublication::getEvent)
                    .filter(MilestoneAdoptedValueAccepted.class::isInstance)
                    .map(MilestoneAdoptedValueAccepted.class::cast)
                    .filter(event -> event.source().reference().equals(receivedRecordId.toString()))
                    .findFirst()
                    .orElseThrow();

            // 続けて 2 回配り直す（前の配信が完了してから次のトランザクションで渡す）
            for (int deliveries = 2; deliveries <= 3; deliveries++) {
                int expected = deliveries;
                transactionTemplate.executeWithoutResult(status -> events.publishEvent(delivered));
                await().atMost(TIMEOUT)
                        .untilAsserted(() -> assertThat(completedAuditPublications(
                                        MilestoneAdoptedValueAccepted.class, receivedRecordId))
                                .isEqualTo(expected));
            }
            assertThat(count(
                            "SELECT COUNT(*) FROM identity.audit_record WHERE target_type = 'RECEIVED_RECORD'"
                                    + " AND target_id = ?",
                            receivedRecordId.toString()))
                    .isEqualTo(1);
            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, receivedRecordId))
                    .containsEntry("occurred_at", occurredAt);
            // 起きないことは呼び出しの記録で確かめる（T-236）。3 回の配信は 3 回とも照会し、追記は最初の 1 回だけ
            Mockito.verify(auditRecords, Mockito.times(3))
                    .existsByActionAndTarget(
                            AuditAction.EXTERNAL_RECORD_ADOPTED, "RECEIVED_RECORD", receivedRecordId.toString());
            Mockito.verify(auditRecords, Mockito.times(1))
                    .append(argThat(auditRecord -> targets(auditRecord, receivedRecordId)));
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行6_監査記録の追記がDBの例外で失敗すると発行記録は完了せず配り直すと監査記録ができる() {
        UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
        try {
            // 受信記録 ID は取込の中で決まり、listener はコミットの直後に動きうるので、失敗させる対象は自分の情報源の受信記録で決める
            Mockito.doThrow(new DataAccessResourceFailureException("監査記録の DB に届かない"))
                    .when(auditRecords)
                    .append(argThat(auditRecord -> isReceivedRecordOf(sourceId, auditRecord)));

            Ingested result = ingested(ingestionService.ingest(
                    steward, milestoneFile(sourceId, milestone(2, "MS-0001", "CTABCDEFGH2345"))));
            UUID receivedRecordId = result.rows().getFirst().receivedRecordId();

            await().atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(failedAuditPublications(receivedRecordId))
                            .isNotEmpty());
            assertThat(completedAuditPublications(MilestoneAdoptedValueAccepted.class, receivedRecordId))
                    .isZero();
            assertThat(count(
                            "SELECT COUNT(*) FROM identity.audit_record WHERE target_type = 'RECEIVED_RECORD'"
                                    + " AND target_id = ?",
                            receivedRecordId.toString()))
                    .isZero();

            // DB が戻った後の配り直し（起動のときの配り直しと同じ経路）で監査記録ができ、発行記録が完了になる
            Mockito.doCallRealMethod()
                    .when(auditRecords)
                    .append(argThat(auditRecord -> isReceivedRecordOf(sourceId, auditRecord)));
            incompleteEventPublications.resubmitIncompletePublications(
                    publication -> publication.getEvent() instanceof MilestoneAdoptedValueAccepted accepted
                            && accepted.source().reference().equals(receivedRecordId.toString()));

            awaitCompletedAuditPublication(MilestoneAdoptedValueAccepted.class, receivedRecordId);
            assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, receivedRecordId))
                    .containsEntry("result", "SUCCESS");
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行9_巻き戻った取込では発行記録が残らず監査のlistenerが呼ばれず監査記録は書かれない() {
        List<UUID> receivedRecordIds = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
            Ingested result = ingested(ingestionService.ingest(
                    steward,
                    milestoneFile(
                            sourceId,
                            milestone(2, "MS-0001", "CTABCDEFGH2345"),
                            milestone(3, "MS-0001", "CTABCDEFGH2346"))));
            result.rows().forEach(row -> receivedRecordIds.add(row.receivedRecordId()));
            status.setRollbackOnly();
        });
        assertThat(receivedRecordIds).hasSize(2);

        for (UUID receivedRecordId : receivedRecordIds) {
            assertThat(count(
                            "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                            "%" + receivedRecordId + "%"))
                    .as("受信記録 %s のイベントの発行記録", receivedRecordId)
                    .isZero();
            assertThat(count(
                            "SELECT COUNT(*) FROM identity.audit_record WHERE target_type = 'RECEIVED_RECORD'"
                                    + " AND target_id = ?",
                            receivedRecordId.toString()))
                    .isZero();
        }
        // 起きないことは呼び出しの記録で確かめる（T-236）。監査の listener が最初にする照会も、追記も呼ばれていない
        assertThat(Mockito.mockingDetails(auditRecords).getInvocations().stream()
                        .map(Invocation::getArguments)
                        .flatMap(Arrays::stream)
                        .filter(argument -> receivedRecordIds.stream()
                                .anyMatch(id -> id.toString().equals(argument)
                                        || (argument instanceof AuditRecord auditRecord && targets(auditRecord, id)))))
                .isEmpty();
    }

    /** 監査の listener の、この受信記録のイベントの発行記録が完了になるまで待つ。 */
    private void awaitCompletedAuditPublication(Class<?> eventType, UUID receivedRecordId) {
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(completedAuditPublications(eventType, receivedRecordId))
                        .isEqualTo(1));
    }

    /** 監査の listener の、この受信記録のイベントの発行記録のうち完了したものの数（受け手と受信記録 ID で絞る）。 */
    private int completedAuditPublications(Class<?> eventType, UUID receivedRecordId) {
        return count(
                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?"
                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                eventType.getName(),
                "%" + receivedRecordId + "%",
                "%" + AUDIT_LISTENER + "%");
    }

    /** 監査の listener の、この受信記録のイベントの発行記録のうち失敗したもの（前例 QuotationPresentedDeliveryIntegrationTest）。 */
    private List<?> failedAuditPublications(UUID receivedRecordId) {
        return eventPublicationRepository.findByStatus(EventPublication.Status.FAILED).stream()
                .filter(publication -> publication.getEvent() instanceof MilestoneAdoptedValueAccepted accepted
                        && accepted.source().reference().equals(receivedRecordId.toString()))
                .filter(publication ->
                        publication.getTargetIdentifier().getValue().contains(AUDIT_LISTENER))
                .toList();
    }

    private Map<String, Object> auditRecordOf(AuditAction action, UUID receivedRecordId) {
        return jdbc.queryForMap(
                "SELECT occurred_at, actor_user_id, result, reason FROM identity.audit_record"
                        + " WHERE action = ? AND target_type = 'RECEIVED_RECORD' AND target_id = ?",
                action.name(),
                receivedRecordId.toString());
    }

    private Object ingestedAt(UUID ingestionId) {
        return jdbc.queryForObject(
                "SELECT ingested_at FROM external_data.ingestion WHERE id = ?", Object.class, ingestionId);
    }

    private static boolean targets(AuditRecord auditRecord, UUID receivedRecordId) {
        return auditRecord != null
                && auditRecord.target() != null
                && receivedRecordId.toString().equals(auditRecord.target().id());
    }

    /** 監査記録の対象がこの情報源の取込の受信記録か（listener はコミットの後に動くので、取込と受信記録は DB で引ける）。 */
    private boolean isReceivedRecordOf(UUID sourceId, AuditRecord auditRecord) {
        return auditRecord != null
                && auditRecord.target() != null
                && "RECEIVED_RECORD".equals(auditRecord.target().type())
                && count(
                                "SELECT COUNT(*) FROM external_data.received_record r"
                                        + " JOIN external_data.ingestion i ON i.id = r.ingestion_id"
                                        + " WHERE r.id = CAST(? AS UUID) AND i.data_source_id = ?",
                                auditRecord.target().id(),
                                sourceId)
                        > 0;
    }

    private IngestedRow milestone(int lineNumber, String externalId, String trackingNumber) {
        Instant acquiredAt = acquiredAt();
        String line = externalId + ",1," + acquiredAt + "," + trackingNumber + ",DEPARTURE,JPTYO," + acquiredAt;
        MilestoneValues values = new MilestoneValues(
                new TrackingNumberValue(trackingNumber),
                MilestoneKindValue.DEPARTURE,
                new Location("JPTYO"),
                new UtcInstant(acquiredAt));
        return new IngestedRow(
                lineNumber, lineNumber, externalId, "1", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestFileCommand milestoneFile(UUID sourceId, IngestedRow... rows) {
        StringBuilder content = new StringBuilder(MILESTONE_HEADER);
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        return new IngestFileCommand(
                sourceId,
                content.toString().getBytes(StandardCharsets.UTF_8),
                rows.length,
                List.of(rows),
                List.of(),
                null);
    }

    /** 寄港 60（上限）の航海のファイル（同じ 2 つの港を 1 日ずつずらして回る）。 */
    private IngestFileCommand voyageFile(UUID sourceId) {
        Instant acquiredAt = acquiredAt();
        List<PortCallValue> calls = new ArrayList<>();
        for (int i = 0; i < VoyageValues.MAX_PORT_CALLS; i++) {
            Instant arrival = FIRST_CALL.plus(Duration.ofDays(i));
            calls.add(new PortCallValue(
                    new Location(i % 2 == 0 ? "JPTYO" : "USLAX"),
                    new UtcInstant(arrival),
                    new UtcInstant(arrival.plus(Duration.ofHours(12)))));
        }
        String portCalls = calls.stream()
                .map(call -> call.port().unLocode() + ";" + call.arrivalAt().instant() + ";"
                        + call.departureAt().instant())
                .collect(Collectors.joining("|"));
        String line = "V2611E,1," + acquiredAt + "," + portCalls;
        IngestedRow row = new IngestedRow(
                2,
                2,
                "V2611E",
                "1",
                acquiredAt,
                new VoyageValues("V2611E", calls),
                line.getBytes(StandardCharsets.UTF_8));
        return new IngestFileCommand(
                sourceId,
                ("external_id,external_version,acquired_at,port_calls\n" + line).getBytes(StandardCharsets.UTF_8),
                1,
                List.of(row),
                List.of(),
                null);
    }

    /** 取得時刻（取込時刻より前。T-230）。 */
    private Instant acquiredAt() {
        return clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    private static Ingested ingested(IngestionOutcome outcome) {
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private UUID insertApprovedSource(String provider, String recordType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, 'APPROVED', 0)",
                id,
                provider,
                recordType);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない。取込が残っていれば消せないので、先に停止中にする。 */
    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update(
                "DELETE FROM external_data.data_source WHERE id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                sourceId,
                sourceId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
