package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateReceivedRecordDecisionException;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DecisionKind;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 隔離の却下のサービス（Bolt 39d2 の行 1〜6・9・10・22。開始準備の決定 (3)・(4)・(6)）。メモリの実装と固定の Clock で確かめる。
 * 「起きないこと」（判断済み・隔離でない・権限なし・ない受信記録で判断を追記せずイベントを出さない）は、判断の追記とイベントの発行の
 * 呼び出しの記録で確かめる（T-236）。
 */
class ReceivedRecordDecisionServiceTest {

    /** ナノ秒を持つ時刻（判断の時刻はマイクロ秒に切り捨てる。行 2）。 */
    private static final Instant NOW = Instant.parse("2026-11-16T01:05:00.123456789Z");

    private final InMemoryIngestionRepository ingestions = new InMemoryIngestionRepository();
    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository();
    /** 判断の追記の呼び出しの記録（T-236）。{@code duplicateOnAdd} なら同時の判断に負けたときの一意制約の違反を投げる（行 6）。 */
    private final List<ReceivedRecordDecision> addCalls = new ArrayList<>();

    private boolean duplicateOnAdd;

    /** 何回目（1 から）の追記で投げるか（Bolt 39d2b の行 8・14。{@code null} を返せば投げない）。 */
    private java.util.function.IntFunction<RuntimeException> failureOnAdd = call -> null;

    private final InMemoryReceivedRecordDecisionRepository decisions = new InMemoryReceivedRecordDecisionRepository() {
        @Override
        public synchronized void add(ReceivedRecordDecision decision) {
            addCalls.add(decision);
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
    private final List<Object> published = new ArrayList<>();
    private final ApplicationEventPublisher publisher = published::add;
    private final ReceivedRecordDecisionService service = new ReceivedRecordDecisionService(
            receivedRecords, decisions, ingestions, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

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
            ReceivedRecordDecisionService withTickingClock = new ReceivedRecordDecisionService(
                    receivedRecords, decisions, ingestions, publisher, new TickingClock(NOW));

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
}
