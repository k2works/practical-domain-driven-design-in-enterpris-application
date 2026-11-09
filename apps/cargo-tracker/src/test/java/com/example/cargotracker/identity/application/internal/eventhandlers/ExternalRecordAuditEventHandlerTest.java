package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 外部原本の採否の監査（Bolt 39b3b。IA-INV-15）。DE-12・DE-13・DE-14 から受信記録ごとに監査記録を 1 つ書く。「起きないこと」（再配信で
 * 重ならない、内容の欠けで書かない）は追記の呼び出しの記録で確かめる（T-236）。
 */
class ExternalRecordAuditEventHandlerTest {

    private static final UtcInstant PUBLISHED_AT = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));
    private static final UtcInstant ACQUIRED_AT = new UtcInstant(Instant.parse("2026-11-15T23:00:00Z"));
    private static final UserId OPERATOR = new UserId(UUID.randomUUID());
    private static final CompanyId OPERATOR_COMPANY = new CompanyId(UUID.randomUUID());
    private static final UUID INGESTION = UUID.randomUUID();
    private static final UUID DATA_SOURCE = UUID.randomUUID();
    private static final UUID ADOPTED_VALUE = UUID.randomUUID();
    private static final String RECEIVED_RECORD = "RECEIVED_RECORD";

    private final RecordingAuditRecords auditRecords = new RecordingAuditRecords();
    private final ExternalRecordAuditEventHandler handler = new ExternalRecordAuditEventHandler(auditRecords);
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExternalRecordAuditEventHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private static Source source(String receivedRecordId) {
        return new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId, ACQUIRED_AT);
    }

    private static VoyageAdoptedValueUpdated voyage(String receivedRecordId, UserId operator) {
        return voyage(receivedRecordId, operator, "V001");
    }

    private static VoyageAdoptedValueUpdated voyage(String receivedRecordId, UserId operator, String voyageNumber) {
        return new VoyageAdoptedValueUpdated(
                ADOPTED_VALUE,
                INGESTION,
                DATA_SOURCE,
                source(receivedRecordId),
                voyageNumber,
                List.of(new VoyageAdoptedValueUpdated.AdoptedPortCall(new Location("JPTYO"), null, ACQUIRED_AT)),
                "9",
                operator,
                OPERATOR_COMPANY,
                PUBLISHED_AT);
    }

    private static MilestoneAdoptedValueAccepted milestone(String receivedRecordId) {
        return milestone(receivedRecordId, "TRK-0001", "M-77");
    }

    private static MilestoneAdoptedValueAccepted milestone(
            String receivedRecordId, String trackingNumber, String externalId) {
        return new MilestoneAdoptedValueAccepted(
                ADOPTED_VALUE,
                INGESTION,
                DATA_SOURCE,
                source(receivedRecordId),
                trackingNumber,
                "DEPARTURE",
                new Location("JPTYO"),
                ACQUIRED_AT,
                externalId,
                "3",
                OPERATOR,
                OPERATOR_COMPANY,
                PUBLISHED_AT);
    }

    @SuppressWarnings("java:S107") // 隔離のイベントの項目をそろえて受ける
    private static ExternalRecordQuarantined quarantined(
            UUID receivedRecordId,
            String externalId,
            String externalVersion,
            String reason,
            Integer firstLine,
            Integer lastLine) {
        return new ExternalRecordQuarantined(
                receivedRecordId,
                INGESTION,
                DATA_SOURCE,
                "P-1",
                "VOYAGE",
                externalId,
                externalVersion,
                reason,
                firstLine,
                lastLine,
                OPERATOR,
                OPERATOR_COMPANY,
                PUBLISHED_AT);
    }

    @Test
    void 行1_DE12を受けると受信記録を対象に採用の監査記録を1つ書く() {
        String receivedRecordId = UUID.randomUUID().toString();

        handler.on(voyage(receivedRecordId, OPERATOR));

        assertThat(auditRecords.appended).singleElement().satisfies(written -> {
            assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_ADOPTED);
            assertThat(written.result()).isEqualTo(AuditResult.SUCCESS);
            assertThat(written.reason()).isNull();
            assertThat(written.actorUserId()).isEqualTo(OPERATOR);
            assertThat(written.actorCompanyId()).isEqualTo(OPERATOR_COMPANY);
            assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT);
            assertThat(written.target())
                    .isEqualTo(new AuditTarget(
                            RECEIVED_RECORD,
                            receivedRecordId,
                            null,
                            null,
                            "recordType=VOYAGE;targetKey=V001;infoVersion=9;ingestionId=" + INGESTION + ";dataSourceId="
                                    + DATA_SOURCE + ";adoptedValueId=" + ADOPTED_VALUE));
        });
    }

    @Test
    void 行2_DE13を受けると追跡番号と外部IDを要約に入れた採用の監査記録を1つ書く() {
        String receivedRecordId = UUID.randomUUID().toString();

        handler.on(milestone(receivedRecordId));

        assertThat(auditRecords.appended).singleElement().satisfies(written -> {
            assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_ADOPTED);
            assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT);
            assertThat(written.target())
                    .isEqualTo(new AuditTarget(
                            RECEIVED_RECORD,
                            receivedRecordId,
                            null,
                            null,
                            "recordType=MILESTONE;targetKey=TRK-0001;externalId=M-77;infoVersion=3;ingestionId="
                                    + INGESTION + ";dataSourceId=" + DATA_SOURCE + ";adoptedValueId=" + ADOPTED_VALUE));
        });
    }

    @Test
    void 行3_内容の違う原本の隔離は理由を記録し外部版を要約に入れて版に入れない() {
        UUID receivedRecordId = UUID.randomUUID();

        handler.on(quarantined(receivedRecordId, "V001", "9", ExternalRecordQuarantined.CONTENT_MISMATCH, 12, 12));

        assertThat(auditRecords.appended).singleElement().satisfies(written -> {
            assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_QUARANTINED);
            assertThat(written.result()).isEqualTo(AuditResult.SUCCESS);
            assertThat(written.reason()).isEqualTo("CONTENT_MISMATCH");
            assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT);
            assertThat(written.target())
                    .isEqualTo(new AuditTarget(
                            RECEIVED_RECORD,
                            receivedRecordId.toString(),
                            null,
                            null,
                            "provider=P-1;recordType=VOYAGE;externalId=V001;externalVersion=9;lines=12-12;ingestionId="
                                    + INGESTION + ";dataSourceId=" + DATA_SOURCE));
        });
    }

    @Test
    void 行4_行の誤りのキーのない隔離は外部IDと外部版を横線にする() {
        UUID receivedRecordId = UUID.randomUUID();

        handler.on(quarantined(receivedRecordId, null, null, "COLUMN_COUNT_MISMATCH", 12, 14));

        assertThat(auditRecords.appended).singleElement().satisfies(written -> {
            assertThat(written.reason()).isEqualTo("COLUMN_COUNT_MISMATCH");
            assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT);
            assertThat(written.target().afterState())
                    .isEqualTo("provider=P-1;recordType=VOYAGE;externalId=-;externalVersion=-;lines=12-14;ingestionId="
                            + INGESTION + ";dataSourceId=" + DATA_SOURCE);
        });
    }

    @Test
    void 行4_ファイルの誤りのキーのない隔離は行も横線にする() {
        UUID receivedRecordId = UUID.randomUUID();

        handler.on(quarantined(receivedRecordId, null, null, "MISSING_COLUMN", null, null));

        assertThat(auditRecords.appended).singleElement().satisfies(written -> {
            assertThat(written.reason()).isEqualTo("MISSING_COLUMN");
            assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT);
            assertThat(written.target().afterState())
                    .isEqualTo("provider=P-1;recordType=VOYAGE;externalId=-;externalVersion=-;lines=-;ingestionId="
                            + INGESTION + ";dataSourceId=" + DATA_SOURCE);
        });
    }

    @Test
    void 行5_同じイベントが再び届いても追記は1回で照会は操作と対象で引く() {
        String receivedRecordId = UUID.randomUUID().toString();
        UUID quarantinedRecordId = UUID.randomUUID();
        VoyageAdoptedValueUpdated adopted = voyage(receivedRecordId, OPERATOR);
        ExternalRecordQuarantined quarantine =
                quarantined(quarantinedRecordId, "V001", "9", ExternalRecordQuarantined.CONTENT_MISMATCH, 1, 1);

        handler.on(adopted);
        handler.on(adopted);
        handler.on(quarantine);
        handler.on(quarantine);

        assertThat(auditRecords.appendCalls).isEqualTo(2);
        assertThat(auditRecords.existsQueries)
                .containsExactly(
                        "EXTERNAL_RECORD_ADOPTED|RECEIVED_RECORD|" + receivedRecordId,
                        "EXTERNAL_RECORD_ADOPTED|RECEIVED_RECORD|" + receivedRecordId,
                        "EXTERNAL_RECORD_QUARANTINED|RECEIVED_RECORD|" + quarantinedRecordId,
                        "EXTERNAL_RECORD_QUARANTINED|RECEIVED_RECORD|" + quarantinedRecordId);
    }

    @Test
    void 行6_追記がDBの失敗で落ちると例外を上げて配り直させる() {
        auditRecords.failing = true;

        VoyageAdoptedValueUpdated event = voyage(UUID.randomUUID().toString(), OPERATOR);
        assertThatThrownBy(() -> handler.on(event)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void 行7_操作者のないイベントは書かずにERRORのログを1つ残して捨てる() {
        String receivedRecordId = UUID.randomUUID().toString();

        handler.on(voyage(receivedRecordId, null));

        assertThat(auditRecords.appendCalls).isZero();
        assertThat(auditRecords.existsQueries).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.ERROR);
            assertThat(log.getFormattedMessage())
                    .contains(receivedRecordId, INGESTION.toString(), "EXTERNAL_RECORD_ADOPTED")
                    .doesNotContain("V001");
        });
    }

    @Test
    void 行7_受信記録IDのない隔離は書かずにERRORのログを1つ残して捨てる() {
        handler.on(quarantined(null, "V001", "9", ExternalRecordQuarantined.CONTENT_MISMATCH, 1, 1));

        assertThat(auditRecords.appendCalls).isZero();
        // 受信記録 ID がないので DB に問い合わせない（内容を確かめてから照会する）
        assertThat(auditRecords.existsQueries).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.ERROR);
            assertThat(log.getFormattedMessage())
                    .contains(INGESTION.toString(), "EXTERNAL_RECORD_QUARANTINED")
                    .doesNotContain("V001");
        });
    }

    @Test
    void 行7_航海番号のないDE12は要約にnullを入れずに捨てる() {
        handler.on(voyage(UUID.randomUUID().toString(), OPERATOR, null));

        assertDiscarded("voyageNumber");
    }

    @Test
    void 行7_追跡番号のないDE13は要約にnullを入れずに捨てる() {
        handler.on(milestone(UUID.randomUUID().toString(), null, "M-77"));

        assertDiscarded("trackingNumber");
    }

    @Test
    void 行7_外部IDのないDE13は要約にnullを入れずに捨てる() {
        handler.on(milestone(UUID.randomUUID().toString(), "TRK-0001", null));

        assertDiscarded("externalId");
    }

    /** 採用の要約に任意の部品はない（「-」はキーのない隔離だけ）ので、欠けは内容の欠けとして捨て、照会も追記もしない。 */
    private void assertDiscarded(String missing) {
        assertThat(auditRecords.appendCalls).isZero();
        assertThat(auditRecords.existsQueries).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.ERROR);
            assertThat(log.getFormattedMessage()).contains("EXTERNAL_RECORD_ADOPTED", missing);
        });
    }

    @Test
    void 行15_発生時刻はイベントの時刻で再配信でも変わらない() {
        String receivedRecordId = UUID.randomUUID().toString();
        MilestoneAdoptedValueAccepted event = milestone(receivedRecordId);

        handler.on(event);
        handler.on(event);

        assertThat(auditRecords.appended)
                .singleElement()
                .satisfies(written -> assertThat(written.occurredAt()).isEqualTo(PUBLISHED_AT));
    }

    /**
     * Bolt 39d2 の行 11（DE-29 受信記録を判断した の監査。開始準備の決定 (6)）。人の判断として新しい操作 {@code EXTERNAL_RECORD_DECIDED} で書き、
     * 理由の列は判断、却下の理由の文は変更後の要約の最後に置く。39b3b の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d2_受信記録の判断の監査 {

        private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2026-11-16T02:00:00.123456Z"));

        private ReceivedRecordDecided decided(UUID receivedRecordId, UserId decidedBy, String reason) {
            return new ReceivedRecordDecided(
                    receivedRecordId,
                    INGESTION,
                    DATA_SOURCE,
                    "REJECTED",
                    reason,
                    decidedBy,
                    OPERATOR_COMPANY,
                    DECIDED_AT);
        }

        @Test
        void 行11_DE29を受けると受信記録を対象に判断の監査記録を1つ書き理由の文は要約の最後に置く() {
            UUID receivedRecordId = UUID.randomUUID();

            handler.on(decided(receivedRecordId, OPERATOR, "提供元に確かめた; 誤送信"));

            assertThat(auditRecords.appended).singleElement().satisfies(written -> {
                assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_DECIDED);
                assertThat(written.result()).isEqualTo(AuditResult.SUCCESS);
                assertThat(written.reason()).isEqualTo("REJECTED");
                assertThat(written.actorUserId()).isEqualTo(OPERATOR);
                assertThat(written.actorCompanyId()).isEqualTo(OPERATOR_COMPANY);
                assertThat(written.occurredAt()).isEqualTo(DECIDED_AT);
                assertThat(written.target())
                        .isEqualTo(new AuditTarget(
                                RECEIVED_RECORD,
                                receivedRecordId.toString(),
                                null,
                                null,
                                "decision=REJECTED;ingestionId=" + INGESTION + ";dataSourceId=" + DATA_SOURCE
                                        + ";reason=提供元に確かめた; 誤送信"));
            });
        }

        @Test
        void 行11_同じDE29が再び届いても追記は1回で照会は判断の操作と対象で引く() {
            UUID receivedRecordId = UUID.randomUUID();
            ReceivedRecordDecided event = decided(receivedRecordId, OPERATOR, "理由");

            handler.on(event);
            handler.on(event);

            assertThat(auditRecords.appendCalls).isEqualTo(1);
            assertThat(auditRecords.existsQueries)
                    .containsExactly(
                            "EXTERNAL_RECORD_DECIDED|RECEIVED_RECORD|" + receivedRecordId,
                            "EXTERNAL_RECORD_DECIDED|RECEIVED_RECORD|" + receivedRecordId);
        }

        @Test
        void 行11_判断者のないDE29は書かずにERRORのログを1つ残して捨てる() {
            UUID receivedRecordId = UUID.randomUUID();

            handler.on(decided(receivedRecordId, null, "理由"));

            assertThat(auditRecords.appendCalls).isZero();
            assertThat(auditRecords.existsQueries).isEmpty();
            assertThat(logs.list).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.ERROR);
                assertThat(log.getFormattedMessage())
                        .contains(receivedRecordId.toString(), INGESTION.toString(), "EXTERNAL_RECORD_DECIDED");
            });
        }

        @Test
        void 行11_理由のないDE29は要約にnullを入れずに捨てる() {
            handler.on(decided(UUID.randomUUID(), OPERATOR, null));

            assertThat(auditRecords.appendCalls).isZero();
            assertThat(logs.list).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.ERROR);
                assertThat(log.getFormattedMessage()).contains("EXTERNAL_RECORD_DECIDED", "reason");
            });
        }
    }

    /**
     * Bolt 39d3b の行 10（理由のない採用の判断の DE-29 の監査。開始準備の決定 (7)）。採用の理由は任意なので、理由のない採用は要約から
     * {@code ;reason=} の部品を省いて書く。却下の理由は必須のままで、理由のない却下は内容の欠けとして捨てる。39d2 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d3b_採用の判断の監査 {

        private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2026-11-16T02:00:00.123456Z"));

        private ReceivedRecordDecided decided(UUID receivedRecordId, String decision, String reason) {
            return new ReceivedRecordDecided(
                    receivedRecordId, INGESTION, DATA_SOURCE, decision, reason, OPERATOR, OPERATOR_COMPANY, DECIDED_AT);
        }

        static Stream<Arguments> summaries() {
            return Stream.of(
                    Arguments.of("理由のない採用", "ADOPTED", null, ""),
                    Arguments.of("空白だけの理由の採用", "ADOPTED", " ", ""),
                    Arguments.of("理由のある採用", "ADOPTED", "提供元に確かめた", ";reason=提供元に確かめた"),
                    Arguments.of("区切りを含む理由の採用", "ADOPTED", "確かめた; 直した", ";reason=確かめた; 直した"),
                    Arguments.of("理由のある却下", "REJECTED", "誤送信", ";reason=誤送信"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("summaries")
        void 行10_判断と理由の有無で要約の理由の部品が決まり理由があれば最後に置く(String name, String decision, String reason, String tail) {
            UUID receivedRecordId = UUID.randomUUID();

            handler.on(decided(receivedRecordId, decision, reason));

            assertThat(auditRecords.appended).singleElement().satisfies(written -> {
                assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_DECIDED);
                assertThat(written.reason()).isEqualTo(decision);
                assertThat(written.occurredAt()).isEqualTo(DECIDED_AT);
                assertThat(written.target())
                        .isEqualTo(new AuditTarget(
                                RECEIVED_RECORD,
                                receivedRecordId.toString(),
                                null,
                                null,
                                "decision=" + decision + ";ingestionId=" + INGESTION + ";dataSourceId=" + DATA_SOURCE
                                        + tail));
            });
            assertThat(logs.list).isEmpty();
        }

        @ParameterizedTest(name = "[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void 行10_理由のない却下は今のまま書かずにERRORのログを残す(String reason) {
            handler.on(decided(UUID.randomUUID(), "REJECTED", reason));

            assertThat(auditRecords.appendCalls).isZero();
            assertThat(auditRecords.existsQueries).isEmpty();
            assertThat(logs.list).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.ERROR);
                assertThat(log.getFormattedMessage()).contains("EXTERNAL_RECORD_DECIDED", "reason");
            });
        }
    }

    /** 追記と照会の呼び出しを記録する監査記録のリポジトリ（T-236）。{@code failing} なら追記で DB の失敗を投げる。 */
    private static final class RecordingAuditRecords implements AuditRecordRepository {

        private final List<AuditRecord> appended = new ArrayList<>();
        private final List<String> existsQueries = new ArrayList<>();
        private int appendCalls;
        private boolean failing;

        @Override
        public void append(AuditRecord auditRecord) {
            appendCalls++;
            if (failing) {
                throw new DataAccessResourceFailureException("監査記録を書けない");
            }
            appended.add(auditRecord);
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return appended.stream().filter(r -> actor.equals(r.actorUserId())).toList();
        }

        @Override
        public Optional<SuspensionRecord> findLatestUserSuspension(UserId target) {
            return Optional.empty();
        }

        @Override
        public boolean existsByActionAndTarget(AuditAction action, String targetType, String targetId) {
            existsQueries.add(action + "|" + targetType + "|" + targetId);
            return appended.stream()
                    .anyMatch(r -> r.action() == action
                            && r.target() != null
                            && r.target().type().equals(targetType)
                            && r.target().id().equals(targetId));
        }
    }
}
