package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateAdoptedValueException;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateReceivedRecordDecisionException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryAdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DecisionKind;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 隔離の却下のサービス（Bolt 39d2 の行 1〜6・9・10・22。開始準備の決定 (3)・(4)・(6)）。メモリの実装と固定の Clock で確かめる。
 * 「起きないこと」（判断済み・隔離でない・権限なし・ない受信記録で判断を追記せずイベントを出さない）は、判断の追記とイベントの発行の
 * 呼び出しの記録で確かめる（T-236）。
 */
class ReceivedRecordDecisionServiceTest {

    /** ナノ秒を持つ時刻（判断の時刻はマイクロ秒に切り捨てる。行 2）。 */
    private static final Instant NOW = Instant.parse("2026-11-16T01:05:00.123456789Z");

    /** 取込・情報源・外部原本の読み出しと判断・採用値の追記の呼び出しの記録（Bolt 39d3b の行 4・5・7・19。T-236）。 */
    private final List<String> calls = new ArrayList<>();

    private final InMemoryIngestionRepository ingestions = new InMemoryIngestionRepository() {
        @Override
        public Optional<Ingestion> findById(UUID id) {
            calls.add(INGESTION_FIND);
            return super.findById(id);
        }
    };
    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository() {
        @Override
        public Optional<ExternalRecord> findById(UUID id) {
            calls.add(EXTERNAL_RECORD_FIND);
            return super.findById(id);
        }
    };
    private final InMemoryDataSourceRepository dataSources = new InMemoryDataSourceRepository() {
        @Override
        public Optional<DataSource> findById(UUID id) {
            calls.add(DATA_SOURCE_FIND);
            return super.findById(id);
        }
    };
    /** 判断の追記の呼び出しの記録（T-236）。{@code duplicateOnAdd} なら同時の判断に負けたときの一意制約の違反を投げる（行 6）。 */
    private final List<ReceivedRecordDecision> addCalls = new ArrayList<>();

    private boolean duplicateOnAdd;

    /** 何回目（1 から）の追記で投げるか（Bolt 39d2b の行 8・14。{@code null} を返せば投げない）。 */
    private java.util.function.IntFunction<RuntimeException> failureOnAdd = call -> null;

    private final InMemoryReceivedRecordDecisionRepository decisions = new InMemoryReceivedRecordDecisionRepository() {
        @Override
        public synchronized void add(ReceivedRecordDecision decision) {
            addCalls.add(decision);
            calls.add(DECISION_ADD);
            RuntimeException failure = failureOnAdd.apply(addCalls.size());
            if (failure != null) {
                throw failure;
            }
            if (duplicateOnAdd) {
                throw DuplicateReceivedRecordDecisionException.of(decision.receivedRecordId(), null);
            }
            super.add(decision);
        }
    };
    private final InMemoryReceivedRecordRepository receivedRecords =
            new InMemoryReceivedRecordRepository(ingestions, externalRecords, decisions);
    private final InMemoryAdoptedValueRepository adoptedValues = new InMemoryAdoptedValueRepository(receivedRecords) {
        @Override
        public synchronized void add(AdoptedValue adoptedValue) {
            calls.add(ADOPTED_VALUE_ADD);
            super.add(adoptedValue);
        }

        @Override
        public Optional<AdoptedValue> currentOf(UUID externalRecordId) {
            calls.add(ADOPTED_VALUE_CURRENT);
            return super.currentOf(externalRecordId);
        }
    };
    private final List<Object> published = new ArrayList<>();
    private final ApplicationEventPublisher publisher = published::add;
    private final ReceivedRecordDecisionService service = serviceWith(Clock.fixed(NOW, ZoneOffset.UTC));

    private static final String INGESTION_FIND = "ingestions.findById";
    private static final String DATA_SOURCE_FIND = "dataSources.findById";
    private static final String EXTERNAL_RECORD_FIND = "externalRecords.findById";
    private static final String DECISION_ADD = "decisions.add";
    private static final String ADOPTED_VALUE_ADD = "adoptedValues.add";
    private static final String ADOPTED_VALUE_CURRENT = "adoptedValues.currentOf";

    /** 判断のサービスを組み立てる（Bolt 39d3b で外部原本・採用値・情報源のリポジトリを足した）。 */
    private ReceivedRecordDecisionService serviceWith(Clock clock) {
        return new ReceivedRecordDecisionService(
                receivedRecords, decisions, ingestions, dataSources, externalRecords, adoptedValues, publisher, clock);
    }

    private final AuthenticatedActor steward = actor(Role.DATA_STEWARD);
    private final UUID dataSourceId = UUID.randomUUID();
    private final Ingestion ingestion = new Ingestion(
            UUID.randomUUID(),
            dataSourceId,
            PayloadObjectKey.newIngestionFile(),
            PayloadHash.of(new byte[] {1}),
            new UserId(UUID.randomUUID()),
            Instant.parse("2026-11-16T01:00:00Z"),
            3,
            null,
            0);

    private static AuthenticatedActor actor(Role role) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(role), "操作者", "運営会社");
    }

    private ReceivedRecord stored(Classification classification, boolean keyless) {
        ingestions.add(ingestion);
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                keyless ? null : UUID.randomUUID(),
                ingestion.id(),
                PayloadHash.of(new byte[] {2}),
                PayloadObjectKey.newRecordPayload(),
                keyless ? null : Instant.parse("2026-11-16T00:30:00Z"),
                classification,
                "注記",
                ingestion.ingestedAt(),
                new PhysicalLineRange(4, 4),
                null);
        receivedRecords.add(received);
        return received;
    }

    private DecisionOutcome reject(AuthenticatedActor actor, UUID receivedRecordId, String reason) {
        return service.reject(actor, new RejectReceivedRecordCommand(receivedRecordId, reason));
    }

    @Test
    void 行1_内容の違う原本の隔離を却下すると判断を1つ追記して却下の結果を返す() {
        ReceivedRecord mismatch = stored(Classification.QUARANTINED, false);

        DecisionOutcome outcome = reject(steward, mismatch.id(), "提供元に確かめ、誤って送った原本と分かった。");

        assertThat(outcome).isEqualTo(new DecisionOutcome.Rejected(mismatch.id()));
        assertThat(decisions.all()).singleElement().satisfies(decision -> {
            assertThat(decision.receivedRecordId()).isEqualTo(mismatch.id());
            assertThat(decision.decision()).isEqualTo(DecisionKind.REJECTED);
            assertThat(decision.decidedBy()).isEqualTo(steward.userId());
            assertThat(decision.reason()).isEqualTo("提供元に確かめ、誤って送った原本と分かった。");
        });
        assertThat(receivedRecords.undecidedQuarantine(mismatch.id())).isEmpty();
        assertThat(service.refusalOf(mismatch.id())).contains(new DecisionOutcome.AlreadyDecided());
    }

    @Test
    void 行2_キーのない隔離も却下でき判断の時刻はClockから1回取りマイクロ秒に切り捨てる() {
        ReceivedRecord keyless = stored(Classification.QUARANTINED, true);

        assertThat(reject(steward, keyless.id(), "直したファイルを取り込み直した。")).isInstanceOf(DecisionOutcome.Rejected.class);

        assertThat(decisions.all())
                .singleElement()
                .extracting(ReceivedRecordDecision::decidedAt)
                .isEqualTo(Instant.parse("2026-11-16T01:05:00.123456Z"));
        assertThat(published)
                .singleElement()
                .extracting(event -> ((ReceivedRecordDecided) event).decidedAt())
                .isEqualTo(new UtcInstant(Instant.parse("2026-11-16T01:05:00.123456Z")));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Classification.class)
    void 行3_隔離でない受信記録は判断を作らず判断できない結果を返し画面のGETも同じ(Classification classification) {
        ReceivedRecord received = stored(classification, false);
        boolean decidable = EnumSet.of(Classification.QUARANTINED).contains(classification);

        DecisionOutcome outcome = reject(steward, received.id(), "理由");

        if (decidable) {
            assertThat(outcome).isInstanceOf(DecisionOutcome.Rejected.class);
        } else {
            assertThat(outcome).isEqualTo(new DecisionOutcome.NotDecidable());
            assertThat(service.refusalOf(received.id())).contains(new DecisionOutcome.NotDecidable());
            assertThat(addCalls).isEmpty();
            assertThat(published).isEmpty();
        }
    }

    @Test
    void 行4_行22_読めない受信記録はないとして判断を作らず画面のGETも同じ() {
        // 取込のない受信記録は findById が読まない（契約のテストの行 22）ので、ない受信記録と同じ結果になる
        UUID missing = UUID.randomUUID();

        assertThat(reject(steward, missing, "理由")).isEqualTo(new DecisionOutcome.NotFound());
        assertThat(service.refusalOf(missing)).contains(new DecisionOutcome.NotFound());
        assertThat(addCalls).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void 行5_判断済みの隔離は判断を追記せず判断済みの結果を返す() {
        ReceivedRecord quarantined = stored(Classification.QUARANTINED, true);
        reject(steward, quarantined.id(), "先の判断。");
        addCalls.clear();
        published.clear();

        DecisionOutcome outcome = reject(actor(Role.DATA_STEWARD), quarantined.id(), "後の判断。");

        assertThat(outcome).isEqualTo(new DecisionOutcome.AlreadyDecided());
        assertThat(addCalls).isEmpty();
        assertThat(published).isEmpty();
        assertThat(decisions.all())
                .singleElement()
                .extracting(ReceivedRecordDecision::reason)
                .isEqualTo("先の判断。");
    }

    @Test
    void 行5_判断を待つ隔離は画面のGETで断らない() {
        ReceivedRecord quarantined = stored(Classification.QUARANTINED, false);

        assertThat(service.refusalOf(quarantined.id())).isEmpty();
    }

    @Test
    void 行6_同時の判断に負けて一意制約に違反すると判断済みの結果を返しイベントを出さない() {
        ReceivedRecord quarantined = stored(Classification.QUARANTINED, true);
        duplicateOnAdd = true;

        DecisionOutcome outcome = reject(steward, quarantined.id(), "理由");

        assertThat(outcome).isEqualTo(new DecisionOutcome.AlreadyDecided());
        assertThat(addCalls).hasSize(1);
        assertThat(published).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = "DATA_STEWARD", mode = EnumSource.Mode.EXCLUDE)
    void 行9_データ責任者でなければ判断を作らず権限なしの結果を返す(Role role) {
        ReceivedRecord quarantined = stored(Classification.QUARANTINED, true);

        assertThat(reject(actor(role), quarantined.id(), "理由")).isEqualTo(new DecisionOutcome.NotPermitted());
        assertThat(addCalls).isEmpty();
        assertThat(published).isEmpty();
        assertThat(receivedRecords.undecidedQuarantine(quarantined.id())).isPresent();
    }

    @Test
    void 行10_却下1つにDE29を1つ出し情報源IDは取込から読み判断者の企業を持つ() {
        ReceivedRecord mismatch = stored(Classification.QUARANTINED, false);

        reject(steward, mismatch.id(), "理由; 区切りを含む");

        assertThat(published)
                .containsExactly(new ReceivedRecordDecided(
                        mismatch.id(),
                        ingestion.id(),
                        dataSourceId,
                        "REJECTED",
                        "理由; 区切りを含む",
                        steward.userId(),
                        steward.companyId(),
                        new UtcInstant(Instant.parse("2026-11-16T01:05:00.123456Z"))));
    }

    @Test
    void 判断の結果は空の理由を持つ却下を受けない() {
        ReceivedRecord quarantined = stored(Classification.QUARANTINED, true);

        // controller が先に拒むが、サービスも拒む（判断の不変条件）。拒んだら何も追記しない
        RejectReceivedRecordCommand blank = new RejectReceivedRecordCommand(quarantined.id(), " ");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.reject(steward, blank))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(addCalls).isEmpty();
        assertThat(published).isEmpty();
        assertThat(receivedRecords.undecidedQuarantine(quarantined.id())).isPresent();
    }

    /**
     * Bolt 39d2b の行 4・8〜12・14（1 つの取込の形式の誤りのまとめての却下。開始準備の決定 (2)・(4)・(5)）。「起きないこと」（0 件・権限なし・
     * ない取込で判断を追記せずイベントを出さない、とばした受信記録の DE-29 を出さない、失敗の後の受信記録を追記しない）は、判断の追記と
     * イベントの発行の呼び出しの記録で確かめる（T-236）。39d2 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d2b_形式の誤りのまとめての却下 {

        private static final String REASON = "列の数を直したファイルを取り込み直した。";

        private final Ingestion other = new Ingestion(
                UUID.randomUUID(),
                dataSourceId,
                PayloadObjectKey.newIngestionFile(),
                PayloadHash.of(new byte[] {3}),
                new UserId(UUID.randomUUID()),
                Instant.parse("2026-11-16T01:01:00Z"),
                1,
                null,
                0);

        @org.junit.jupiter.api.BeforeEach
        void 取込がある() {
            ingestions.add(ingestion);
            ingestions.add(other);
        }

        /** 呼ぶたびに 1 秒進む Clock（判断の時刻を 1 回だけ取ることを確かめる。固定の Clock では受信記録ごとに取っても同じ時刻になる）。 */
        private static final class TickingClock extends Clock {

            private final Instant start;
            private int calls;

            TickingClock(Instant start) {
                this.start = start;
            }

            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return start.plusSeconds(calls++);
            }
        }

        /** その取込のキーのない隔離（範囲が null ならファイルの誤り）。 */
        private ReceivedRecord keyless(Ingestion of, PhysicalLineRange range) {
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    null,
                    of.id(),
                    PayloadHash.of(new byte[] {4}),
                    PayloadObjectKey.newRecordPayload(),
                    null,
                    Classification.QUARANTINED,
                    "形式の誤り",
                    of.ingestedAt(),
                    range,
                    null);
            receivedRecords.add(received);
            return received;
        }

        private ReceivedRecord mismatch(int line) {
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ingestion.id(),
                    PayloadHash.of(new byte[] {5}),
                    PayloadObjectKey.newRecordPayload(),
                    Instant.parse("2026-11-16T00:30:00Z"),
                    Classification.QUARANTINED,
                    "内容の違う原本",
                    ingestion.ingestedAt(),
                    new PhysicalLineRange(line, line),
                    null);
            receivedRecords.add(received);
            return received;
        }

        private KeylessRejectionOutcome rejectKeyless(AuthenticatedActor actor, UUID ingestionId) {
            return service.rejectKeyless(actor, new RejectKeylessQuarantinesCommand(ingestionId, REASON));
        }

        @Test
        void 行4_その取込の判断を待つ形式の誤りのすべてに同じ判断者と時刻と理由の却下を1つずつ追記し内容の違う原本とほかの取込は残す() {
            ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(4, 4));
            ReceivedRecord fileError = keyless(ingestion, null);
            ReceivedRecord untilEnd = keyless(ingestion, new PhysicalLineRange(7, 9));
            ReceivedRecord contentMismatch = mismatch(3);
            ReceivedRecord ofOther = keyless(other, new PhysicalLineRange(2, 2));

            KeylessRejectionOutcome outcome = rejectKeyless(steward, ingestion.id());

            assertThat(outcome).isEqualTo(new KeylessRejectionOutcome.RejectedAll(3));
            assertThat(decisions.all())
                    .extracting(ReceivedRecordDecision::receivedRecordId)
                    .containsExactly(fileError.id(), rowError.id(), untilEnd.id());
            assertThat(decisions.all()).allSatisfy(decision -> {
                assertThat(decision.decision()).isEqualTo(DecisionKind.REJECTED);
                assertThat(decision.decidedBy()).isEqualTo(steward.userId());
                assertThat(decision.decidedAt()).isEqualTo(Instant.parse("2026-11-16T01:05:00.123456Z"));
                assertThat(decision.reason()).isEqualTo(REASON);
            });
            assertThat(receivedRecords.undecidedQuarantine(contentMismatch.id()))
                    .isPresent();
            assertThat(receivedRecords.undecidedQuarantine(ofOther.id())).isPresent();
        }

        @Test
        void 行4_判断の時刻は呼ぶたびに進むClockからも1回だけ取り全件で同じにする() {
            keyless(ingestion, new PhysicalLineRange(2, 2));
            keyless(ingestion, new PhysicalLineRange(3, 3));
            keyless(ingestion, new PhysicalLineRange(4, 4));
            ReceivedRecordDecisionService withTickingClock = serviceWith(new TickingClock(NOW));

            withTickingClock.rejectKeyless(steward, new RejectKeylessQuarantinesCommand(ingestion.id(), REASON));

            assertThat(decisions.all())
                    .extracting(ReceivedRecordDecision::decidedAt)
                    .containsOnly(Instant.parse("2026-11-16T01:05:00.123456Z"));
        }

        @Test
        void 行8_読んだ後に1件ずつ却下された受信記録の一意制約の違反はとばして数えずDE29も出さずほかは却下する() {
            ReceivedRecord first = keyless(ingestion, new PhysicalLineRange(2, 2));
            ReceivedRecord decidedMeanwhile = keyless(ingestion, new PhysicalLineRange(3, 3));
            ReceivedRecord third = keyless(ingestion, new PhysicalLineRange(4, 4));
            failureOnAdd =
                    call -> call == 2 ? DuplicateReceivedRecordDecisionException.of(decidedMeanwhile.id(), null) : null;

            KeylessRejectionOutcome outcome = rejectKeyless(steward, ingestion.id());

            assertThat(outcome).isEqualTo(new KeylessRejectionOutcome.RejectedAll(2));
            assertThat(addCalls).hasSize(3);
            assertThat(published)
                    .extracting(event -> ((ReceivedRecordDecided) event).receivedRecordId())
                    .containsExactly(first.id(), third.id());
        }

        @Test
        void 行9_判断を待つ形式の誤りが0件なら判断を作らずイベントを出さず却下するものがない結果を返す() {
            mismatch(3);
            keyless(other, new PhysicalLineRange(2, 2));

            assertThat(rejectKeyless(steward, ingestion.id())).isEqualTo(new KeylessRejectionOutcome.NothingToReject());
            assertThat(addCalls).isEmpty();
            assertThat(published).isEmpty();
        }

        @Test
        void 行9_二重送信の2度目は判断を作らず却下するものがない結果を返す() {
            keyless(ingestion, new PhysicalLineRange(2, 2));
            assertThat(rejectKeyless(steward, ingestion.id())).isEqualTo(new KeylessRejectionOutcome.RejectedAll(1));
            addCalls.clear();
            published.clear();

            assertThat(rejectKeyless(actor(Role.DATA_STEWARD), ingestion.id()))
                    .isEqualTo(new KeylessRejectionOutcome.NothingToReject());
            assertThat(addCalls).isEmpty();
            assertThat(published).isEmpty();
        }

        @Test
        void 行9_全部がとばされたら却下するものがない結果を返しイベントを出さない() {
            ReceivedRecord one = keyless(ingestion, new PhysicalLineRange(2, 2));
            ReceivedRecord two = keyless(ingestion, new PhysicalLineRange(3, 3));
            List<UUID> ids = List.of(one.id(), two.id());
            failureOnAdd = call -> DuplicateReceivedRecordDecisionException.of(ids.get(call - 1), null);

            assertThat(rejectKeyless(steward, ingestion.id())).isEqualTo(new KeylessRejectionOutcome.NothingToReject());
            assertThat(addCalls).hasSize(2);
            assertThat(published).isEmpty();
        }

        @Test
        void 行10_ない取込は判断を作らずないの結果を返す() {
            keyless(ingestion, new PhysicalLineRange(2, 2));

            assertThat(rejectKeyless(steward, UUID.randomUUID())).isEqualTo(new KeylessRejectionOutcome.NotFound());
            assertThat(addCalls).isEmpty();
            assertThat(published).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Role.class, names = "DATA_STEWARD", mode = EnumSource.Mode.EXCLUDE)
        void 行11_データ責任者でなければ判断を作らず権限なしの結果を返す(Role role) {
            ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(2, 2));

            assertThat(rejectKeyless(actor(role), ingestion.id()))
                    .isEqualTo(new KeylessRejectionOutcome.NotPermitted());
            assertThat(addCalls).isEmpty();
            assertThat(published).isEmpty();
            assertThat(receivedRecords.undecidedQuarantine(rowError.id())).isPresent();
        }

        @Test
        void 行12_却下した受信記録ごとに1件ずつの却下と同じ形のDE29を1つ出す() {
            ReceivedRecord fileError = keyless(ingestion, null);
            ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(4, 4));

            rejectKeyless(steward, ingestion.id());

            UtcInstant decidedAt = new UtcInstant(Instant.parse("2026-11-16T01:05:00.123456Z"));
            assertThat(published)
                    .containsExactly(
                            new ReceivedRecordDecided(
                                    fileError.id(),
                                    ingestion.id(),
                                    dataSourceId,
                                    "REJECTED",
                                    REASON,
                                    steward.userId(),
                                    steward.companyId(),
                                    decidedAt),
                            new ReceivedRecordDecided(
                                    rowError.id(),
                                    ingestion.id(),
                                    dataSourceId,
                                    "REJECTED",
                                    REASON,
                                    steward.userId(),
                                    steward.companyId(),
                                    decidedAt));
        }

        @Test
        void 行14_一意制約の違反でない追記の失敗は握りつぶさず上げ後の受信記録を追記しない() {
            keyless(ingestion, new PhysicalLineRange(2, 2));
            keyless(ingestion, new PhysicalLineRange(3, 3));
            keyless(ingestion, new PhysicalLineRange(4, 4));
            failureOnAdd =
                    call -> call == 2 ? new org.springframework.dao.DataAccessResourceFailureException("DB の失敗") : null;

            RejectKeylessQuarantinesCommand command = new RejectKeylessQuarantinesCommand(ingestion.id(), REASON);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.rejectKeyless(steward, command))
                    .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
            assertThat(addCalls).hasSize(2);
        }
    }

    /**
     * Bolt 39d3b の行 2・4〜7・19・20（航海の内容の違う原本の隔離の採用。開始準備の決定 (4)・(5)・(6)）。メモリの実装と固定の Clock で確かめる。
     * 「起きないこと」（採用を出さない隔離・断る順・同時の判断で判断と採用値を追記せずイベントを出さない、取込・情報源・外部原本を引かない、
     * この外部版の採用値を引かない）と書く順は、呼び出しの記録で確かめる（T-236）。39d2 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d3b_航海の隔離の採用 {

        private static final String PROVIDER = "開発海運（架空）";
        private static final String REASON = "提供元に確かめ、到着の時刻を直した原本と分かった。";
        private static final Instant ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant DECIDED_AT = Instant.parse("2026-11-16T01:05:00.123456Z");

        /** 東京を出発し、ロサンゼルスに到着する寄港 2 つの航海（ロサンゼルスの到着の時刻だけを変えられる。T-230）。 */
        private static VoyageValues voyage(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private static final VoyageValues RECEIVED = voyage("2026-12-01T22:00:00Z");

        private static MilestoneValues milestone() {
            return new MilestoneValues(
                    new TrackingNumberValue("CTABCDEFGH2345"),
                    MilestoneKindValue.DEPARTURE,
                    new Location("JPTYO"),
                    new UtcInstant(Instant.parse("2026-11-18T23:00:00Z")));
        }

        /** 情報源と取込（情報源の承認の状態を変えられる）。 */
        private void source(DataSource.ApprovalStatus status) {
            dataSources.add(new DataSource(dataSourceId, PROVIDER, "VOYAGE", status));
            ingestions.add(ingestion);
        }

        private ExternalRecord externalRecord(String recordType) {
            ExternalRecord externalRecord = new ExternalRecord(
                    UUID.randomUUID(),
                    new IdempotencyKey(PROVIDER, recordType, "DEMO-201", "3"),
                    PayloadHash.of(new byte[] {6}),
                    PayloadObjectKey.newRecordPayload(),
                    Instant.parse("2026-11-15T09:00:00Z"));
            externalRecords.add(externalRecord);
            return externalRecord;
        }

        /** 外部原本の内容の違う原本の隔離（値がなければ Bolt 39d3 より前の隔離）。 */
        private ReceivedRecord quarantine(ExternalRecord externalRecord, ReceivedValue value) {
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecord.id(),
                    ingestion.id(),
                    PayloadHash.of(new byte[] {7}),
                    PayloadObjectKey.newRecordPayload(),
                    ACQUIRED_AT,
                    Classification.QUARANTINED,
                    "3 行目: 内容の違う原本",
                    ingestion.ingestedAt(),
                    new PhysicalLineRange(3, 3),
                    value);
            receivedRecords.add(received);
            return received;
        }

        /** 承認済みの航海の情報源の、受け取った値を読める内容の違う原本の隔離。 */
        private ReceivedRecord adoptable() {
            source(DataSource.ApprovalStatus.APPROVED);
            return quarantine(externalRecord("VOYAGE"), ReceivedValue.of(RECEIVED));
        }

        private DecisionOutcome adopt(AuthenticatedActor actor, UUID receivedRecordId, String reason) {
            return service.adopt(actor, new AdoptReceivedRecordCommand(receivedRecordId, reason));
        }

        private long callsOf(String call) {
            return calls.stream().filter(call::equals).count();
        }

        private void assertNothingWritten() {
            assertThat(callsOf(DECISION_ADD)).isZero();
            assertThat(callsOf(ADOPTED_VALUE_ADD)).isZero();
            assertThat(decisions.all()).isEmpty();
            assertThat(published).isEmpty();
        }

        // --- 行 2: 採用（航海） ---

        @ParameterizedTest(name = "この外部版の採用値がある {0}")
        @ValueSource(booleans = {true, false})
        void 行2_航海の内容の違う原本を採用すると判断と採用値を追記しDE29とDE12を出して採用した結果を返す(boolean withCurrent) {
            ReceivedRecord received = adoptable();
            if (withCurrent) {
                // 同じ外部原本の、取込で採用した値（この外部版の採用値）
                ReceivedRecord original = new ReceivedRecord(
                        UUID.randomUUID(),
                        received.externalRecordId(),
                        ingestion.id(),
                        PayloadHash.of(new byte[] {8}),
                        PayloadObjectKey.newRecordPayload(),
                        Instant.parse("2026-11-15T08:30:00Z"),
                        Classification.ADOPTED,
                        null,
                        ingestion.ingestedAt(),
                        new PhysicalLineRange(2, 2),
                        ReceivedValue.of(voyage("2026-12-01T14:00:00Z")));
                receivedRecords.add(original);
                adoptedValues.add(new AdoptedValue(
                        UUID.randomUUID(),
                        original.id(),
                        AdoptedValue.TargetType.VOYAGE,
                        "DEMO-201",
                        voyage("2026-12-01T14:00:00Z").summary(),
                        "3",
                        ingestion.ingestedAt()));
                calls.clear();
            }

            DecisionOutcome outcome = adopt(steward, received.id(), REASON);

            assertThat(outcome).isEqualTo(new DecisionOutcome.AdoptedQuarantine(received.id()));
            assertThat(decisions.all()).singleElement().satisfies(decision -> {
                assertThat(decision.receivedRecordId()).isEqualTo(received.id());
                assertThat(decision.decision()).isEqualTo(DecisionKind.ADOPTED);
                assertThat(decision.decidedBy()).isEqualTo(steward.userId());
                assertThat(decision.decidedAt()).isEqualTo(DECIDED_AT);
                assertThat(decision.reason()).isEqualTo(REASON);
            });
            AdoptedValue adoptedValue =
                    adoptedValues.findByReceivedRecordId(received.id()).orElseThrow();
            assertThat(adoptedValue.targetType()).isEqualTo(AdoptedValue.TargetType.VOYAGE);
            assertThat(adoptedValue.targetKey()).isEqualTo("DEMO-201");
            assertThat(adoptedValue.valueSummary()).isEqualTo(RECEIVED.summary());
            assertThat(adoptedValue.infoVersion()).isEqualTo("3");
            assertThat(adoptedValue.publishedAt()).isEqualTo(DECIDED_AT);
            assertThat(published)
                    .containsExactly(
                            new ReceivedRecordDecided(
                                    received.id(),
                                    ingestion.id(),
                                    dataSourceId,
                                    "ADOPTED",
                                    REASON,
                                    steward.userId(),
                                    steward.companyId(),
                                    new UtcInstant(DECIDED_AT)),
                            new VoyageAdoptedValueUpdated(
                                    adoptedValue.id(),
                                    ingestion.id(),
                                    dataSourceId,
                                    new Source(
                                            SourceKind.EXTERNAL_RECORD,
                                            received.id().toString(),
                                            new UtcInstant(ACQUIRED_AT)),
                                    "DEMO-201",
                                    RECEIVED.portCalls().stream()
                                            .map(call -> new VoyageAdoptedValueUpdated.AdoptedPortCall(
                                                    call.port(), call.arrivalAt(), call.departureAt()))
                                            .toList(),
                                    "3",
                                    steward.userId(),
                                    steward.companyId(),
                                    new UtcInstant(DECIDED_AT)));
            assertThat(receivedRecords.undecidedQuarantine(received.id())).isEmpty();
        }

        // --- 行 4: 採用を出さない隔離（GET と POST で 1 つの定め） ---

        /** 採用を出さない隔離の作り方と、取込・情報源・外部原本を引く数（T-244）。 */
        record NotAdoptableCase(
                String name,
                java.util.function.Function<Bolt39d3b_航海の隔離の採用, ReceivedRecord> create,
                int ingestionFinds,
                int dataSourceFinds,
                int externalRecordFinds) {

            @Override
            public String toString() {
                return name;
            }
        }

        static Stream<NotAdoptableCase> notAdoptable() {
            return Stream.of(
                    new NotAdoptableCase("(a) キーのない隔離", test -> test.keylessQuarantine(), 0, 0, 0),
                    new NotAdoptableCase(
                            "(b) 値の列のない隔離",
                            test -> {
                                test.source(DataSource.ApprovalStatus.APPROVED);
                                return test.quarantine(test.externalRecord("VOYAGE"), null);
                            },
                            0,
                            0,
                            0),
                    new NotAdoptableCase(
                            "(c) 受け取った値の要約が壊れている",
                            test -> {
                                test.source(DataSource.ApprovalStatus.APPROVED);
                                return test.quarantine(
                                        test.externalRecord("VOYAGE"), new ReceivedValue("DEMO-201", "壊れた要約"));
                            },
                            1,
                            1,
                            1),
                    new NotAdoptableCase(
                            "(d) 空でない知らない原本種別",
                            test -> {
                                test.source(DataSource.ApprovalStatus.APPROVED);
                                return test.quarantine(test.externalRecord("CONTAINER"), ReceivedValue.of(RECEIVED));
                            },
                            1,
                            1,
                            1),
                    new NotAdoptableCase(
                            "(e) 主要実績の隔離（39d3b2 まで）",
                            test -> {
                                test.source(DataSource.ApprovalStatus.APPROVED);
                                return test.quarantine(test.externalRecord("MILESTONE"), ReceivedValue.of(milestone()));
                            },
                            1,
                            1,
                            1),
                    new NotAdoptableCase(
                            "(f) 停止した情報源の隔離",
                            test -> {
                                test.source(DataSource.ApprovalStatus.SUSPENDED);
                                return test.quarantine(test.externalRecord("VOYAGE"), ReceivedValue.of(RECEIVED));
                            },
                            1,
                            1,
                            0));
        }

        private ReceivedRecord keylessQuarantine() {
            ingestions.add(ingestion);
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    null,
                    ingestion.id(),
                    PayloadHash.of(new byte[] {9}),
                    PayloadObjectKey.newRecordPayload(),
                    null,
                    Classification.QUARANTINED,
                    "4 行目: 形式の誤り",
                    ingestion.ingestedAt(),
                    new PhysicalLineRange(4, 4),
                    null);
            receivedRecords.add(received);
            return received;
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("notAdoptable")
        void 行4_採用を出さない隔離は判断も採用値も追記せずイベントを出さず採用できるかの照会も同じ結果を返す(NotAdoptableCase notAdoptableCase) {
            ReceivedRecord received = notAdoptableCase.create().apply(this);
            calls.clear();

            DecisionOutcome outcome = adopt(steward, received.id(), REASON);

            assertThat(outcome).isEqualTo(new DecisionOutcome.NotAdoptable());
            assertNothingWritten();
            assertThat(callsOf(INGESTION_FIND)).isEqualTo(notAdoptableCase.ingestionFinds());
            assertThat(callsOf(DATA_SOURCE_FIND)).isEqualTo(notAdoptableCase.dataSourceFinds());
            assertThat(callsOf(EXTERNAL_RECORD_FIND)).isEqualTo(notAdoptableCase.externalRecordFinds());
            assertThat(service.adoptionRefusalOf(received.id())).contains(new DecisionOutcome.NotAdoptable());
            assertThat(receivedRecords.undecidedQuarantine(received.id())).isPresent();
        }

        @Test
        void 行4_採用できる隔離では採用できるかの照会は空を返し判断を追記しない() {
            ReceivedRecord received = adoptable();

            assertThat(service.adoptionRefusalOf(received.id())).isEmpty();
            assertNothingWritten();
        }

        // --- 行 5: 採用値の側だけ読めない隔離 ---

        @Test
        void 行5_この外部版の採用値の要約が壊れていても受け取った値を読めれば採用でき採用値を引かない() {
            ReceivedRecord received = adoptable();
            ReceivedRecord original = new ReceivedRecord(
                    UUID.randomUUID(),
                    received.externalRecordId(),
                    ingestion.id(),
                    PayloadHash.of(new byte[] {10}),
                    PayloadObjectKey.newRecordPayload(),
                    Instant.parse("2026-11-15T08:30:00Z"),
                    Classification.ADOPTED,
                    null,
                    ingestion.ingestedAt(),
                    new PhysicalLineRange(2, 2),
                    ReceivedValue.of(voyage("2026-12-01T14:00:00Z")));
            receivedRecords.add(original);
            adoptedValues.add(new AdoptedValue(
                    UUID.randomUUID(),
                    original.id(),
                    AdoptedValue.TargetType.VOYAGE,
                    "DEMO-201",
                    "壊れた要約",
                    "3",
                    ingestion.ingestedAt()));
            calls.clear();

            assertThat(service.adoptionRefusalOf(received.id())).isEmpty();
            assertThat(adopt(steward, received.id(), null))
                    .isEqualTo(new DecisionOutcome.AdoptedQuarantine(received.id()));

            assertThat(adoptedValues.findByReceivedRecordId(received.id()))
                    .get()
                    .extracting(AdoptedValue::valueSummary)
                    .isEqualTo(RECEIVED.summary());
            assertThat(published).hasSize(2).last().isInstanceOf(VoyageAdoptedValueUpdated.class);
            assertThat(callsOf(ADOPTED_VALUE_CURRENT)).isZero();
        }

        // --- 行 6: 断る順（却下と同じ順で、採用できるかより先） ---

        @Test
        void 行6_データ責任者でなければ取込も情報源も外部原本も引かず権限なしを返す() {
            ReceivedRecord received = adoptable();
            calls.clear();

            for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
                assertThat(adopt(actor(role), received.id(), REASON)).isEqualTo(new DecisionOutcome.NotPermitted());
            }
            assertThat(calls).isEmpty();
            assertNothingWritten();
        }

        static Stream<Arguments> refusals() {
            return Stream.of(
                    Arguments.of("ない受信記録", new DecisionOutcome.NotFound()),
                    Arguments.of("隔離でない受信記録", new DecisionOutcome.NotDecidable()),
                    Arguments.of("判断済み", new DecisionOutcome.AlreadyDecided()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusals")
        void 行6_ない受信記録と隔離でない受信記録と判断済みは採用できるかより先に断り取込も情報源も外部原本も引かない(String name, DecisionOutcome refusal) {
            source(DataSource.ApprovalStatus.APPROVED);
            ExternalRecord externalRecord = externalRecord("VOYAGE");
            UUID receivedRecordId =
                    switch (refusal) {
                        case DecisionOutcome.NotFound _ -> UUID.randomUUID();
                        case DecisionOutcome.NotDecidable _ -> {
                            ReceivedRecord adopted = new ReceivedRecord(
                                    UUID.randomUUID(),
                                    externalRecord.id(),
                                    ingestion.id(),
                                    PayloadHash.of(new byte[] {11}),
                                    PayloadObjectKey.newRecordPayload(),
                                    ACQUIRED_AT,
                                    Classification.ADOPTED,
                                    null,
                                    ingestion.ingestedAt(),
                                    new PhysicalLineRange(2, 2),
                                    ReceivedValue.of(RECEIVED));
                            receivedRecords.add(adopted);
                            yield adopted.id();
                        }
                        default -> {
                            ReceivedRecord decided = quarantine(externalRecord, ReceivedValue.of(RECEIVED));
                            decisions.add(ReceivedRecordDecision.reject(decided, "先の却下。", steward.userId(), NOW));
                            yield decided.id();
                        }
                    };
            calls.clear();
            published.clear();
            int decisionsBefore = decisions.all().size();

            assertThat(adopt(steward, receivedRecordId, REASON)).isEqualTo(refusal);
            assertThat(service.adoptionRefusalOf(receivedRecordId)).contains(refusal);

            assertThat(calls).doesNotContain(INGESTION_FIND, DATA_SOURCE_FIND, EXTERNAL_RECORD_FIND, ADOPTED_VALUE_ADD);
            assertThat(callsOf(DECISION_ADD)).isZero();
            assertThat(decisions.all()).hasSize(decisionsBefore);
            assertThat(published).isEmpty();
        }

        // --- 行 7・19: 同時の判断と書く順 ---

        @Test
        void 行7_同時の判断に負けて一意制約に違反すると判断済みを返し採用値を追記せずイベントを出さない() {
            ReceivedRecord received = adoptable();
            duplicateOnAdd = true;
            calls.clear();

            assertThat(adopt(steward, received.id(), REASON)).isEqualTo(new DecisionOutcome.AlreadyDecided());

            assertThat(callsOf(DECISION_ADD)).isEqualTo(1);
            assertThat(callsOf(ADOPTED_VALUE_ADD)).isZero();
            assertThat(adoptedValues.findByReceivedRecordId(received.id())).isEmpty();
            assertThat(published).isEmpty();
        }

        @Test
        void 行7_採用値の一意制約の違反は判断済みにせず捕まえずに上げてイベントを出さない() {
            ReceivedRecord received = adoptable();
            adoptedValues.add(new AdoptedValue(
                    UUID.randomUUID(),
                    received.id(),
                    AdoptedValue.TargetType.VOYAGE,
                    "DEMO-201",
                    RECEIVED.summary(),
                    "3",
                    ingestion.ingestedAt()));

            UUID receivedRecordId = received.id();

            assertThatThrownBy(() -> adopt(steward, receivedRecordId, REASON))
                    .isInstanceOf(DuplicateAdoptedValueException.class);

            assertThat(published).isEmpty();
        }

        @Test
        void 行19_判断の追記が採用値の追記より先に呼ばれる() {
            ReceivedRecord received = adoptable();
            calls.clear();

            adopt(steward, received.id(), REASON);

            assertThat(calls.stream()
                            .filter(call -> call.equals(DECISION_ADD) || call.equals(ADOPTED_VALUE_ADD))
                            .toList())
                    .containsExactly(DECISION_ADD, ADOPTED_VALUE_ADD);
        }

        // --- 行 20: 時刻 ---

        @Test
        void 行20_判断の時刻は呼ぶたびに進むClockからも1回だけ取り判断と採用値とDE12とDE29で同じにする() {
            ReceivedRecord received = adoptable();
            ReceivedRecordDecisionService withTickingClock = serviceWith(new Bolt39d2b_形式の誤りのまとめての却下.TickingClock(NOW));

            withTickingClock.adopt(steward, new AdoptReceivedRecordCommand(received.id(), null));

            assertThat(decisions.all())
                    .singleElement()
                    .extracting(ReceivedRecordDecision::decidedAt)
                    .isEqualTo(DECIDED_AT);
            assertThat(adoptedValues.findByReceivedRecordId(received.id()))
                    .get()
                    .extracting(AdoptedValue::publishedAt)
                    .isEqualTo(DECIDED_AT);
            assertThat(published)
                    .extracting(event -> event instanceof ReceivedRecordDecided decided
                            ? decided.decidedAt()
                            : ((VoyageAdoptedValueUpdated) event).publishedAt())
                    .containsExactly(new UtcInstant(DECIDED_AT), new UtcInstant(DECIDED_AT));
        }
    }
}
