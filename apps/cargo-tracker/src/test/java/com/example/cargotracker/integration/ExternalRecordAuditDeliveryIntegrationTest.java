package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.KeylessRejectionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
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
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
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
    EventSerializer eventSerializer;

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
            MilestoneAdoptedValueAccepted delivered =
                    completedAuditEvent(MilestoneAdoptedValueAccepted.class, receivedRecordId);

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

    /**
     * Bolt 39d2 の行 12（DE-29 の監査の配信。T-184）。却下のコミットの後に監査の listener が DE-29 を受け、判断の監査記録を 1 つ書く。巻き戻った
     * 却下には発行記録も監査記録もない。39b3b の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d2_判断の監査の配信 {

        @Autowired
        ReceivedRecordDecisionService decisionService;

        /** 内容の違う原本の隔離を 1 つ取り込み、その受信記録 ID を返す。 */
        private UUID quarantined(UUID sourceId) {
            Ingested result = ingested(ingestionService.ingest(
                    steward,
                    milestoneFile(
                            sourceId,
                            milestone(2, "MS-0001", "CTABCDEFGH2345"),
                            milestone(3, "MS-0001", "CTABCDEFGH2346"))));
            return result.rows().get(1).receivedRecordId();
        }

        @Test
        void 行12_却下のコミットの後に監査のlistenerがDE29を受け判断の監査記録を1つ書き発行記録が完了になる() {
            UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
            try {
                UUID receivedRecordId = quarantined(sourceId);

                DecisionOutcome outcome = decisionService.reject(
                        steward, new RejectReceivedRecordCommand(receivedRecordId, "提供元に確かめた; 誤送信"));

                assertThat(outcome).isEqualTo(new DecisionOutcome.Rejected(receivedRecordId));
                awaitCompletedAuditPublication(ReceivedRecordDecided.class, receivedRecordId);
                assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_DECIDED, receivedRecordId))
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("actor_user_id", steward.userId().value())
                        .containsEntry("reason", "REJECTED")
                        .containsEntry(
                                "occurred_at",
                                jdbc.queryForObject(
                                        "SELECT decided_at FROM external_data.received_record_decision"
                                                + " WHERE received_record_id = ?",
                                        Object.class,
                                        receivedRecordId));
                assertThat(count(
                                "SELECT COUNT(*) FROM identity.audit_record WHERE action = 'EXTERNAL_RECORD_DECIDED'"
                                        + " AND target_type = 'RECEIVED_RECORD' AND target_id = ?",
                                receivedRecordId.toString()))
                        .isEqualTo(1);
            } finally {
                removeSource(sourceId);
            }
        }

        @Test
        void 行12_巻き戻った却下には判断の発行記録も監査記録もなく監査のlistenerは呼ばれない() {
            UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
            try {
                UUID receivedRecordId = quarantined(sourceId);

                transactionTemplate.executeWithoutResult(status -> {
                    assertThat(decisionService.reject(
                                    steward, new RejectReceivedRecordCommand(receivedRecordId, "巻き戻す却下")))
                            .isInstanceOf(DecisionOutcome.Rejected.class);
                    status.setRollbackOnly();
                });

                assertThat(count(
                                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ?",
                                ReceivedRecordDecided.class.getName(),
                                "%" + receivedRecordId + "%"))
                        .isZero();
                assertThat(count(
                                "SELECT COUNT(*) FROM external_data.received_record_decision WHERE received_record_id = ?",
                                receivedRecordId))
                        .isZero();
                assertThat(count(
                                "SELECT COUNT(*) FROM identity.audit_record WHERE action = 'EXTERNAL_RECORD_DECIDED'"
                                        + " AND target_id = ?",
                                receivedRecordId.toString()))
                        .isZero();
                // 起きないことは呼び出しの記録で確かめる（T-236）。判断の監査の照会は呼ばれていない
                Mockito.verify(auditRecords, Mockito.never())
                        .existsByActionAndTarget(
                                AuditAction.EXTERNAL_RECORD_DECIDED, "RECEIVED_RECORD", receivedRecordId.toString());
            } finally {
                removeSource(sourceId);
            }
        }
    }

    /**
     * Bolt 39d2b の行 13（まとめての却下の DE-29 の監査の配信。T-184、IA-INV-15）。まとめて 3 件を却下したコミットの後に監査の listener が
     * 受信記録ごとに DE-29 を受け、判断の監査記録を受信記録ごとに 1 つずつ、計 3 つ書く。39d2 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d2b_まとめての却下の監査の配信 {

        @Autowired
        ReceivedRecordDecisionService decisionService;

        @Test
        void 行13_まとめて3件を却下したコミットの後に監査のlistenerが受信記録ごとに判断の監査記録を1つずつ書く() {
            UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID(), "MILESTONE");
            try {
                Instant acquiredAt = acquiredAt();
                String valid = "MS-0001,1," + acquiredAt + ",CTABCDEFGH2345,DEPARTURE,JPTYO," + acquiredAt;
                // 3 行は列が 1 つ多い（行の誤りのキーのない隔離。T-230）
                String csv = MILESTONE_HEADER + "\n" + valid + "\n" + valid.replace("MS-0001", "MS-0002") + ",余分\n"
                        + valid.replace("MS-0001", "MS-0003") + ",余分\n" + valid.replace("MS-0001", "MS-0004")
                        + ",余分\n";
                Ingested result = ingested(ingestionService.ingest(
                        steward,
                        com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler.assemble(
                                sourceId,
                                com.example.cargotracker.externaldata.interfaces.file.CsvFileKind.MILESTONE,
                                csv.getBytes(StandardCharsets.UTF_8))));
                List<UUID> keyless = jdbc.queryForList(
                        "SELECT id FROM external_data.received_record WHERE ingestion_id = ?"
                                + " AND external_record_id IS NULL",
                        UUID.class,
                        result.ingestionId());
                assertThat(keyless).hasSize(3);

                KeylessRejectionOutcome outcome = decisionService.rejectKeyless(
                        steward, new RejectKeylessQuarantinesCommand(result.ingestionId(), "直したファイルを取り込み直した。"));

                assertThat(outcome).isEqualTo(new KeylessRejectionOutcome.RejectedAll(3));
                for (UUID receivedRecordId : keyless) {
                    awaitCompletedAuditPublication(ReceivedRecordDecided.class, receivedRecordId);
                    assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_DECIDED, receivedRecordId))
                            .containsEntry("result", "SUCCESS")
                            .containsEntry("actor_user_id", steward.userId().value())
                            .containsEntry("reason", "REJECTED");
                }
                assertThat(count(
                                "SELECT COUNT(*) FROM identity.audit_record a JOIN external_data.received_record r"
                                        + " ON a.target_id = CAST(r.id AS VARCHAR)"
                                        + " WHERE a.action = 'EXTERNAL_RECORD_DECIDED' AND a.target_type = 'RECEIVED_RECORD'"
                                        + " AND r.ingestion_id = ?",
                                result.ingestionId()))
                        .isEqualTo(3);
            } finally {
                removeSource(sourceId);
            }
        }
    }

    /**
     * Bolt 39d3b の行 11（採用の判断の監査の配信。T-184、IA-INV-15）。航海の内容の違う原本の隔離を理由なしで採用したコミットの後に、監査の listener が
     * DE-29 と DE-12 を受け、隔離の受信記録に判断の監査記録（理由は判断）と採用の監査記録を 1 つずつ書き、取込のときの隔離の監査記録と合わせて 3 つになる。
     * 同じイベントを配り直しても増えない。主要実績（DE-13）は 39d3b2 で足す。39b3b の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d3b_採用の判断の監査の配信 {

        @Autowired
        ReceivedRecordDecisionService decisionService;

        private IngestedRow voyageRow(int lineNumber, Instant losAngelesArrival) {
            Instant acquiredAt = acquiredAt();
            VoyageValues values = new VoyageValues(
                    "V2611E",
                    List.of(
                            new PortCallValue(new Location("JPTYO"), null, new UtcInstant(FIRST_CALL)),
                            new PortCallValue(new Location("USLAX"), new UtcInstant(losAngelesArrival), null)));
            String line = "V2611E,1," + acquiredAt + ",JPTYO;;" + FIRST_CALL + "|USLAX;" + losAngelesArrival + ";";
            return new IngestedRow(
                    lineNumber, lineNumber, "V2611E", "1", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
        }

        /** 航海の内容の違う原本の隔離を 1 つ取り込み、その受信記録 ID を返す。 */
        private UUID quarantinedVoyage(UUID sourceId) {
            IngestedRow first = voyageRow(2, FIRST_CALL.plus(Duration.ofDays(10)));
            IngestedRow changed = voyageRow(3, FIRST_CALL.plus(Duration.ofDays(11)));
            Ingested result = ingested(ingestionService.ingest(
                    steward,
                    new IngestFileCommand(
                            sourceId,
                            ("external_id,external_version,acquired_at,port_calls\n"
                                            + new String(first.payload(), StandardCharsets.UTF_8) + "\n"
                                            + new String(changed.payload(), StandardCharsets.UTF_8))
                                    .getBytes(StandardCharsets.UTF_8),
                            2,
                            List.of(first, changed),
                            List.of(),
                            null)));
            assertThat(result.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
            return result.rows().get(1).receivedRecordId();
        }

        @Test
        void 行11_理由のない採用のコミットの後に判断と採用の監査記録が1つずつ書かれ隔離の監査記録と合わせて3つで配り直しても増えない() {
            UUID sourceId = insertApprovedSource("船会社-" + UUID.randomUUID(), "VOYAGE");
            try {
                UUID receivedRecordId = quarantinedVoyage(sourceId);

                DecisionOutcome outcome =
                        decisionService.adopt(steward, new AdoptReceivedRecordCommand(receivedRecordId, null));

                assertThat(outcome).isEqualTo(new DecisionOutcome.AdoptedQuarantine(receivedRecordId));
                awaitCompletedAuditPublication(ExternalRecordQuarantined.class, receivedRecordId);
                awaitCompletedAuditPublication(ReceivedRecordDecided.class, receivedRecordId);
                awaitCompletedAuditPublication(VoyageAdoptedValueUpdated.class, receivedRecordId);
                Object decidedAt = jdbc.queryForObject(
                        "SELECT decided_at FROM external_data.received_record_decision WHERE received_record_id = ?",
                        Object.class,
                        receivedRecordId);
                assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_DECIDED, receivedRecordId))
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("actor_user_id", steward.userId().value())
                        .containsEntry("reason", "ADOPTED")
                        .containsEntry("occurred_at", decidedAt);
                assertThat(auditRecordOf(AuditAction.EXTERNAL_RECORD_ADOPTED, receivedRecordId))
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("actor_user_id", steward.userId().value())
                        .containsEntry("reason", null)
                        .containsEntry("occurred_at", decidedAt);
                assertThat(auditRecordCountOf(receivedRecordId)).isEqualTo(3);

                ReceivedRecordDecided decided = completedAuditEvent(ReceivedRecordDecided.class, receivedRecordId);
                VoyageAdoptedValueUpdated updated =
                        completedAuditEvent(VoyageAdoptedValueUpdated.class, receivedRecordId);
                transactionTemplate.executeWithoutResult(status -> {
                    events.publishEvent(decided);
                    events.publishEvent(updated);
                });
                await().atMost(TIMEOUT).untilAsserted(() -> {
                    assertThat(completedAuditPublications(ReceivedRecordDecided.class, receivedRecordId))
                            .isEqualTo(2);
                    assertThat(completedAuditPublications(VoyageAdoptedValueUpdated.class, receivedRecordId))
                            .isEqualTo(2);
                });
                assertThat(auditRecordCountOf(receivedRecordId)).isEqualTo(3);
            } finally {
                removeSource(sourceId);
            }
        }

        private int auditRecordCountOf(UUID receivedRecordId) {
            return count(
                    "SELECT COUNT(*) FROM identity.audit_record WHERE target_type = 'RECEIVED_RECORD' AND target_id = ?",
                    receivedRecordId.toString());
        }
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

    /**
     * 監査の listener が受け終えた、この受信記録のイベント（受け手と受信記録 ID で SQL で絞る。共有の DB の発行記録をすべて読み込まない）。
     */
    private <T> T completedAuditEvent(Class<T> eventType, UUID receivedRecordId) {
        String serialized = jdbc.queryForObject(
                "SELECT serialized_event FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?"
                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                String.class,
                eventType.getName(),
                "%" + receivedRecordId + "%",
                "%" + AUDIT_LISTENER + "%");
        return eventType.cast(eventSerializer.deserialize(serialized, eventType));
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
