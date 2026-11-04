package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

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
    private final InMemoryReceivedRecordDecisionRepository decisions = new InMemoryReceivedRecordDecisionRepository() {
        @Override
        public synchronized void add(ReceivedRecordDecision decision) {
            addCalls.add(decision);
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
                new PhysicalLineRange(4, 4));
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
}
