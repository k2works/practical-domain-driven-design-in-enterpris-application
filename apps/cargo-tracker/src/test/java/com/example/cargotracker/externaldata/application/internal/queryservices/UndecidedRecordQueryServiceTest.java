package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryAdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 判断を待つ隔離の照会（S-17 の一覧。Bolt 39d の行 1・2・5・8・9・13。開始準備の決定 (3)〜(5)、T-236・T-240・T-244）。ポートを mock にし、
 * 種類（外部原本の有無）と外部 ID・外部版の組、ページの値（件数・頁・前後・示す範囲）と照会に渡す offset、情報源をページの中の異なる ID ごとに
 * 1 回だけ引くこと、0 件ならページの照会と情報源を引かないこと、絞った取込がないことを、呼び出しの記録とともに確かめる。
 */
class UndecidedRecordQueryServiceTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-11-16T01:05:00Z");

    private final ReceivedRecordRepository receivedRecords = mock(ReceivedRecordRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
    /** この外部版の採用値の照会（Bolt 39d3。判断の画面の値の照会だけが引く）。 */
    private final AdoptedValueRepository adoptedValues = mock(AdoptedValueRepository.class);

    private final UndecidedRecordQueryService service =
            new UndecidedRecordQueryService(receivedRecords, ingestions, dataSources, adoptedValues);

    private final DataSource voyage =
            new DataSource(UUID.randomUUID(), "照会の提供元-" + UUID.randomUUID(), "VOYAGE", ApprovalStatus.APPROVED);
    private final DataSource milestone =
            new DataSource(UUID.randomUUID(), "照会の提供元-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.APPROVED);
    private final UUID ingestionId = UUID.randomUUID();

    @BeforeEach
    void 情報源がある() {
        given(dataSources.findById(voyage.id())).willReturn(Optional.of(voyage));
        given(dataSources.findById(milestone.id())).willReturn(Optional.of(milestone));
    }

    /** 物理行の (line) 行目のキーのない隔離（行の誤り）。 */
    private UndecidedRecord keyless(DataSource source, int line) {
        return new UndecidedRecord(
                UUID.randomUUID(),
                Classification.QUARANTINED,
                ingestionId,
                INGESTED_AT,
                source.id(),
                new PhysicalLineRange(line, line),
                null,
                null,
                line + " 行目: 値が空です（external_id）。提供元に確かめてください。");
    }

    private void givenUndecided(UUID filter, int total) {
        given(receivedRecords.countUndecidedRecords(filter)).willReturn(total);
        given(receivedRecords.undecidedRecords(any(), anyInt(), anyInt())).willAnswer(invocation -> {
            int offset = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return IntStream.range(offset, Math.min(total, offset + limit))
                    .mapToObj(n -> keyless(voyage, n + 2))
                    .toList();
        });
    }

    // --- 行 1・2: 種類と外部 ID・外部版の組（T-240・T-244） ---

    @ParameterizedTest(name = "外部原本 {0}")
    @CsvSource(
            nullValues = "NULL",
            value = {"あり, DEMO-201, 3, false", "なし, NULL, NULL, true"})
    void 行1_行2_外部原本の有無で種類が決まり外部原本のある隔離だけが外部ID外部版を持つ(
            String externalRecord, String externalId, String externalVersion, boolean keyless) {
        UndecidedRecord quarantine = new UndecidedRecord(
                UUID.randomUUID(),
                Classification.QUARANTINED,
                ingestionId,
                INGESTED_AT,
                voyage.id(),
                new PhysicalLineRange(140, 141),
                externalId,
                externalVersion,
                "140 行目から 141 行目まで: 注記");
        given(receivedRecords.countUndecidedRecords(null)).willReturn(1);
        given(receivedRecords.undecidedRecords(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
                .willReturn(List.of(quarantine));

        UndecidedRecordPage.Item item = service.page(null, 1).items().getFirst();

        assertThat(item)
                .isEqualTo(new UndecidedRecordPage.Item(
                        quarantine.receivedRecordId(),
                        ingestionId,
                        INGESTED_AT,
                        voyage.provider(),
                        "VOYAGE",
                        new PhysicalLineRange(140, 141),
                        Classification.QUARANTINED,
                        externalId,
                        externalVersion,
                        "140 行目から 141 行目まで: 注記"));
    }

    @Test
    void 行1_行2_期待の組は外部原本のある隔離とない隔離の両方を含む() {
        // 一覧に出る分類は隔離だけ（採用・重複は照会が返さない。契約のテストの行 3）。隔離の 2 つの種類をすべて縛る（T-240）
        UndecidedRecord withKey = new UndecidedRecord(
                UUID.randomUUID(),
                Classification.QUARANTINED,
                ingestionId,
                INGESTED_AT,
                voyage.id(),
                null,
                "V-1",
                "1",
                "注記");
        UndecidedRecord withoutKey = keyless(voyage, 2);
        given(receivedRecords.countUndecidedRecords(null)).willReturn(2);
        given(receivedRecords.undecidedRecords(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
                .willReturn(List.of(withKey, withoutKey));

        assertThat(service.page(null, 1).items())
                .extracting(UndecidedRecordPage.Item::keyless)
                .containsExactly(false, true);
    }

    // --- 行 5: ページの境（T-244） ---

    @ParameterizedTest(name = "{0} 件の {1} ページ目を求めると {2} ページ目")
    @CsvSource({
        // 件数, 求めた頁, 示す頁, 頁の数, offset, 前, 次, 示す最初, 示す最後
        "0,     1,   1,   0,   -1,   false, false, 0,    0",
        "100,   1,   1,   1,   0,    false, false, 1,    100",
        "101,   1,   1,   2,   0,    false, true,  1,    100",
        "101,   2,   2,   2,   100,  true,  false, 101,  101",
        "10000, 1,   1,   100, 0,    false, true,  1,    100",
        "10000, 50,  50,  100, 4900, true,  true,  4901, 5000",
        "10000, 100, 100, 100, 9900, true,  false, 9901, 10000",
        "10000, 101, 100, 100, 9900, true,  false, 9901, 10000",
        "10000, 0,   1,   100, 0,    false, true,  1,    100",
        "10000, -3,  1,   100, 0,    false, true,  1,    100"
    })
    void 行5_件数と求めた頁からページの値を決め最後より後は最後の頁に寄せる(
            int total,
            int requested,
            int page,
            int pageCount,
            int offset,
            boolean previous,
            boolean next,
            int firstShown,
            int lastShown) {
        givenUndecided(null, total);

        UndecidedRecordPage result = service.page(null, requested);

        assertThat(result.total()).isEqualTo(total);
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.pageCount()).isEqualTo(pageCount);
        assertThat(result.hasPrevious()).isEqualTo(previous);
        assertThat(result.hasNext()).isEqualTo(next);
        assertThat(result.items()).hasSize(lastShown == 0 ? 0 : lastShown - firstShown + 1);
        if (total > 0) {
            assertThat(result.firstShown()).isEqualTo(firstShown);
            assertThat(result.lastShown()).isEqualTo(lastShown);
            then(receivedRecords).should().undecidedRecords(null, offset, UndecidedRecordQueryService.PAGE_SIZE);
        }
    }

    // --- 行 8: 情報源の引き方 ---

    @Test
    void 行8_情報源はページの中の異なる情報源IDごとに1回だけ引き取込は引かない() {
        given(receivedRecords.countUndecidedRecords(null)).willReturn(4);
        given(receivedRecords.undecidedRecords(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
                .willReturn(
                        List.of(keyless(voyage, 2), keyless(milestone, 3), keyless(voyage, 4), keyless(milestone, 5)));

        UndecidedRecordPage result = service.page(null, 1);

        assertThat(result.items())
                .extracting(UndecidedRecordPage.Item::recordType)
                .containsExactly("VOYAGE", "MILESTONE", "VOYAGE", "MILESTONE");
        then(dataSources).should(times(1)).findById(voyage.id());
        then(dataSources).should(times(1)).findById(milestone.id());
        then(ingestions).should(never()).findById(any());
    }

    // --- 行 9: 全体で 0 件（T-236） ---

    @Test
    void 行9_判断を待つ隔離が0件ならページの照会と取込と情報源を引かない() {
        given(receivedRecords.countUndecidedRecords(null)).willReturn(0);

        UndecidedRecordPage result = service.page(null, 1);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.ingestionFilter()).isNull();
        assertThat(result.ingestionNotFound()).isFalse();
        then(receivedRecords).should(never()).undecidedRecords(any(), anyInt(), anyInt());
        then(ingestions).should(never()).findById(any());
        then(dataSources).should(never()).findById(any());
    }

    // --- 行 13: 絞った取込の有無 ---

    @Test
    void 行13_絞った取込があればその取込で数えて読み取込は1回だけ引く() {
        given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
        givenUndecided(ingestionId, 2);

        UndecidedRecordPage result = service.page(ingestionId, 1);

        assertThat(result.ingestionFilter()).isEqualTo(ingestionId);
        assertThat(result.ingestionNotFound()).isFalse();
        assertThat(result.total()).isEqualTo(2);
        then(receivedRecords).should().undecidedRecords(ingestionId, 0, UndecidedRecordQueryService.PAGE_SIZE);
        then(ingestions).should(times(1)).findById(ingestionId);
    }

    @Test
    void 行13_絞った取込がなければ絞らない一覧を返し見つからないことを示す() {
        UUID missing = UUID.randomUUID();
        given(ingestions.findById(missing)).willReturn(Optional.empty());
        givenUndecided(null, 3);

        UndecidedRecordPage result = service.page(missing, 1);

        assertThat(result.ingestionFilter()).isNull();
        assertThat(result.ingestionNotFound()).isTrue();
        assertThat(result.total()).isEqualTo(3);
        then(receivedRecords).should(never()).countUndecidedRecords(missing);
        then(receivedRecords).should().undecidedRecords(null, 0, UndecidedRecordQueryService.PAGE_SIZE);
        then(ingestions).should(times(1)).findById(missing);
    }

    /** Bolt 39d2 の行 18（判断の画面の中身。開始準備の決定 (3)）。ID で絞った判断を待つ隔離を一覧と同じ項目にする。 */
    @Nested
    class Bolt39d2_判断の画面の中身 {

        @Test
        void 行18_IDで絞った判断を待つ隔離を一覧と同じ項目で返し情報源を1回引く() {
            UndecidedRecord mismatch = new UndecidedRecord(
                    UUID.randomUUID(),
                    Classification.QUARANTINED,
                    ingestionId,
                    INGESTED_AT,
                    voyage.id(),
                    new PhysicalLineRange(140, 141),
                    "DEMO-201",
                    "3",
                    "140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。");
            given(receivedRecords.undecidedRecord(mismatch.receivedRecordId())).willReturn(Optional.of(mismatch));

            assertThat(service.undecidedRecord(mismatch.receivedRecordId()))
                    .contains(new UndecidedRecordPage.Item(
                            mismatch.receivedRecordId(),
                            ingestionId,
                            INGESTED_AT,
                            voyage.provider(),
                            "VOYAGE",
                            new PhysicalLineRange(140, 141),
                            Classification.QUARANTINED,
                            "DEMO-201",
                            "3",
                            mismatch.note()));
            then(dataSources).should(times(1)).findById(voyage.id());
        }

        @Test
        void 行18_キーのない隔離は形式の誤りの印を持つ() {
            UndecidedRecord keyless = keyless(milestone, 4);
            given(receivedRecords.undecidedRecord(keyless.receivedRecordId())).willReturn(Optional.of(keyless));

            assertThat(service.undecidedRecord(keyless.receivedRecordId())).hasValueSatisfying(item -> {
                assertThat(item.keyless()).isTrue();
                assertThat(item.recordType()).isEqualTo("MILESTONE");
            });
        }

        @Test
        void 行5_判断を待つ隔離でなければ空で情報源を引かない() {
            UUID decided = UUID.randomUUID();
            given(receivedRecords.undecidedRecord(decided)).willReturn(Optional.empty());

            assertThat(service.undecidedRecord(decided)).isEmpty();
            then(dataSources).should(never()).findById(any());
        }
    }

    /**
     * Bolt 39d2b の行 2・3・15・16（一覧のキーのない隔離の件数、まとめての却下の画面の中身、件数と 1 ページの照会の間に件数が減ったときの
     * 読み直し。開始準備の決定 (3)・(8)、T-236）。数え直さないこと・数えないことは呼び出しの記録で確かめる。
     */
    @Nested
    class Bolt39d2b_まとめての却下と件数が減ったとき {

        // --- 行 2: 一覧のキーのない隔離の件数 ---

        @Test
        void 行2_取込で絞った一覧はその取込の判断を待つキーのない隔離を数えてページに持たせる() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
            givenUndecided(ingestionId, 3);
            given(receivedRecords.countUndecidedKeylessQuarantines(ingestionId)).willReturn(2);

            UndecidedRecordPage result = service.page(ingestionId, 1);

            assertThat(result.keylessQuarantineCount()).isEqualTo(2);
            then(receivedRecords).should(times(1)).countUndecidedKeylessQuarantines(ingestionId);
        }

        @Test
        void 行2_絞らない一覧はキーのない隔離を数えない() {
            givenUndecided(null, 3);

            assertThat(service.page(null, 1).keylessQuarantineCount()).isZero();
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
        }

        @Test
        void 行2_絞った取込が見つからない一覧はキーのない隔離を数えない() {
            UUID missing = UUID.randomUUID();
            given(ingestions.findById(missing)).willReturn(Optional.empty());
            givenUndecided(null, 3);

            assertThat(service.page(missing, 1).keylessQuarantineCount()).isZero();
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
        }

        @Test
        void 行2_取込で絞って判断を待つ隔離が0件ならキーのない隔離を数えない() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
            given(receivedRecords.countUndecidedRecords(ingestionId)).willReturn(0);

            assertThat(service.page(ingestionId, 1).keylessQuarantineCount()).isZero();
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
        }

        // --- 行 3: まとめての却下の画面の中身 ---

        @Test
        void 行3_まとめての却下の画面の中身は取込の時刻と情報源とその取込の判断を待つキーのない隔離の件数() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
            given(receivedRecords.countUndecidedKeylessQuarantines(ingestionId)).willReturn(9_900);

            assertThat(service.keylessRejectionTarget(ingestionId))
                    .contains(new KeylessRejectionTarget(ingestionId, INGESTED_AT, voyage.provider(), "VOYAGE", 9_900));
            then(dataSources).should(times(1)).findById(voyage.id());
        }

        @Test
        void 行10_ない取込のまとめての却下の画面の中身は空で数えず情報源を引かない() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.empty());

            assertThat(service.keylessRejectionTarget(ingestionId)).isEmpty();
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
            then(dataSources).should(never()).findById(any());
        }

        // --- 行 15・16: 件数と 1 ページの照会の間に件数が減ったとき（39d の申し送り） ---

        @Test
        void 行15_読んだページが空で件数が0より大きければ数え直して最後のページを1回だけ読み直す() {
            given(receivedRecords.countUndecidedRecords(null)).willReturn(101, 100);
            given(receivedRecords.undecidedRecords(null, 100, UndecidedRecordQueryService.PAGE_SIZE))
                    .willReturn(List.of());
            given(receivedRecords.undecidedRecords(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
                    .willReturn(IntStream.range(0, 100)
                            .mapToObj(n -> keyless(voyage, n + 2))
                            .toList());

            UndecidedRecordPage result = service.page(null, 2);

            assertThat(result.total()).isEqualTo(100);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.pageCount()).isEqualTo(1);
            assertThat(result.items()).hasSize(100);
            assertThat(result.firstShown()).isEqualTo(1);
            assertThat(result.lastShown()).isEqualTo(100);
            assertThat(result.hasNext()).isFalse();
            then(receivedRecords).should(times(2)).countUndecidedRecords(null);
            then(receivedRecords).should(times(2)).undecidedRecords(any(), anyInt(), anyInt());
        }

        @Test
        void 行15_読んだページが空でなければ数え直さない() {
            givenUndecided(null, 101);

            service.page(null, 2);

            then(receivedRecords).should(times(1)).countUndecidedRecords(null);
            then(receivedRecords).should(times(1)).undecidedRecords(any(), anyInt(), anyInt());
        }

        @Test
        void 行16_数え直して0件なら読み直さず0件のページを返す() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
            given(receivedRecords.countUndecidedRecords(ingestionId)).willReturn(1, 0);
            given(receivedRecords.undecidedRecords(any(), anyInt(), anyInt())).willReturn(List.of());

            UndecidedRecordPage result = service.page(ingestionId, 1);

            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.pageCount()).isZero();
            assertThat(result.ingestionFilter()).isEqualTo(ingestionId);
            assertThat(result.keylessQuarantineCount()).isZero();
            then(receivedRecords).should(times(1)).undecidedRecords(any(), anyInt(), anyInt());
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
        }

        @Test
        void 行16_読み直しても空なら0件のページを返す() {
            given(receivedRecords.countUndecidedRecords(null)).willReturn(201, 150);
            given(receivedRecords.undecidedRecords(any(), anyInt(), anyInt())).willReturn(List.of());

            UndecidedRecordPage result = service.page(null, 3);

            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(result.firstShown()).isZero();
            assertThat(result.lastShown()).isZero();
            then(receivedRecords).should().undecidedRecords(null, 200, UndecidedRecordQueryService.PAGE_SIZE);
            then(receivedRecords).should().undecidedRecords(null, 100, UndecidedRecordQueryService.PAGE_SIZE);
            then(receivedRecords).should(times(2)).countUndecidedRecords(null);
        }
    }

    /**
     * Bolt 39d3b の行 8（採用した後のこの外部版の採用値。開始準備の決定 (6)、【業務の判断】(j)、T-245）。メモリの実装で、同じ外部原本の隔離の採用が
     * もう 1 つの隔離の比べる相手を変え、別の外部版の隔離の比べる相手は変えないことを確かめる。39d3 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d3b_採用した後のこの外部版の採用値 {

        private static final Instant ORIGINAL_ACQUIRED_AT = Instant.parse("2026-11-15T08:30:00Z");
        private static final Instant ORIGINAL_PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");
        private static final Instant FIRST_ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant DECIDED_AT = Instant.parse("2026-11-16T02:00:00.123456Z");
        private static final String DEFAULT_ARRIVAL = "2026-12-01T14:00:00Z";
        private static final String ADOPTED_ARRIVAL = "2026-12-01T22:00:00Z";
        private static final String OTHER_ARRIVAL = "2026-12-02T03:00:00Z";

        private final InMemoryIngestionRepository memoryIngestions = new InMemoryIngestionRepository();
        private final InMemoryExternalRecordRepository memoryExternalRecords = new InMemoryExternalRecordRepository();
        private final InMemoryReceivedRecordRepository memoryReceivedRecords = new InMemoryReceivedRecordRepository(
                memoryIngestions, memoryExternalRecords, new InMemoryReceivedRecordDecisionRepository());
        private final InMemoryAdoptedValueRepository memoryAdoptedValues =
                new InMemoryAdoptedValueRepository(memoryReceivedRecords);
        private final UndecidedRecordQueryService memoryService = new UndecidedRecordQueryService(
                memoryReceivedRecords, memoryIngestions, dataSources, memoryAdoptedValues);

        private static VoyageValues voyageArriving(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private ExternalRecord externalRecord(String externalVersion) {
            ExternalRecord externalRecord = new ExternalRecord(
                    UUID.randomUUID(),
                    new IdempotencyKey(voyage.provider(), "VOYAGE", "DEMO-201", externalVersion),
                    PayloadHash.of(new byte[] {1}),
                    PayloadObjectKey.newRecordPayload(),
                    ORIGINAL_PUBLISHED_AT);
            memoryExternalRecords.add(externalRecord);
            return externalRecord;
        }

        private ReceivedRecord stored(
                ExternalRecord externalRecord,
                Classification classification,
                Instant acquiredAt,
                String losAngelesArrival) {
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecord.id(),
                    ingestionId,
                    PayloadHash.of(new byte[] {2}),
                    PayloadObjectKey.newRecordPayload(),
                    acquiredAt,
                    classification,
                    classification == Classification.ADOPTED ? null : "注記",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    ReceivedValue.of(voyageArriving(losAngelesArrival)));
            memoryReceivedRecords.add(received);
            return received;
        }

        private AdoptedValue adoptedValue(
                ReceivedRecord received, String externalVersion, String losAngelesArrival, Instant publishedAt) {
            return new AdoptedValue(
                    UUID.randomUUID(),
                    received.id(),
                    TargetType.VOYAGE,
                    "DEMO-201",
                    voyageArriving(losAngelesArrival).summary(),
                    externalVersion,
                    publishedAt);
        }

        private UndecidedRecordPage.Item item(ReceivedRecord received, String externalVersion) {
            return new UndecidedRecordPage.Item(
                    received.id(),
                    ingestionId,
                    INGESTED_AT,
                    voyage.provider(),
                    "VOYAGE",
                    new PhysicalLineRange(3, 3),
                    Classification.QUARANTINED,
                    "DEMO-201",
                    externalVersion,
                    "注記");
        }

        @ParameterizedTest(name = "採用した値を先に追記する {0}")
        @ValueSource(booleans = {false, true})
        void 行8_同じ外部原本の1つ目の隔離を採用すると2つ目の比べる相手は採用した値になり追記の順によらない(boolean adoptedFirst) {
            ExternalRecord externalRecord = externalRecord("3");
            ReceivedRecord original =
                    stored(externalRecord, Classification.ADOPTED, ORIGINAL_ACQUIRED_AT, DEFAULT_ARRIVAL);
            ReceivedRecord first =
                    stored(externalRecord, Classification.QUARANTINED, FIRST_ACQUIRED_AT, ADOPTED_ARRIVAL);
            ReceivedRecord second =
                    stored(externalRecord, Classification.QUARANTINED, FIRST_ACQUIRED_AT, OTHER_ARRIVAL);
            AdoptedValue ofIngestion = adoptedValue(original, "3", DEFAULT_ARRIVAL, ORIGINAL_PUBLISHED_AT);
            AdoptedValue ofDecision = adoptedValue(first, "3", ADOPTED_ARRIVAL, DECIDED_AT);
            for (AdoptedValue adopted :
                    adoptedFirst ? List.of(ofDecision, ofIngestion) : List.of(ofIngestion, ofDecision)) {
                memoryAdoptedValues.add(adopted);
            }

            assertThat(memoryService.undecidedRecordValues(item(second, "3")))
                    .get()
                    .extracting(UndecidedRecordValues::adopted)
                    .isEqualTo(new UndecidedRecordValues.Adopted(
                            voyageArriving(ADOPTED_ARRIVAL), "3", DECIDED_AT, FIRST_ACQUIRED_AT));
        }

        @Test
        void 行8_同じ外部IDの別の外部版の隔離の比べる相手は採用で変わらない() {
            ExternalRecord adoptedRecord = externalRecord("3");
            ExternalRecord otherVersion = externalRecord("4");
            ReceivedRecord first =
                    stored(adoptedRecord, Classification.QUARANTINED, FIRST_ACQUIRED_AT, ADOPTED_ARRIVAL);
            memoryAdoptedValues.add(adoptedValue(first, "3", ADOPTED_ARRIVAL, DECIDED_AT));
            ReceivedRecord otherOriginal =
                    stored(otherVersion, Classification.ADOPTED, ORIGINAL_ACQUIRED_AT, DEFAULT_ARRIVAL);
            memoryAdoptedValues.add(adoptedValue(otherOriginal, "4", DEFAULT_ARRIVAL, ORIGINAL_PUBLISHED_AT));
            ReceivedRecord otherQuarantine =
                    stored(otherVersion, Classification.QUARANTINED, FIRST_ACQUIRED_AT, OTHER_ARRIVAL);

            assertThat(memoryService.undecidedRecordValues(item(otherQuarantine, "4")))
                    .get()
                    .extracting(UndecidedRecordValues::adopted)
                    .isEqualTo(new UndecidedRecordValues.Adopted(
                            voyageArriving(DEFAULT_ARRIVAL), "4", ORIGINAL_PUBLISHED_AT, ORIGINAL_ACQUIRED_AT));
        }
    }

    /**
     * Bolt 39d3 の行 10・12・13（判断の画面の値の照会。開始準備の決定 (5)・(6)、T-236）。引く数と引かないことは呼び出しの記録で確かめる。
     * 主要実績の値と読めない値は {@link Bolt39d3c_主要実績と読めない値の照会}。
     */
    @Nested
    class Bolt39d3_判断の画面の値の照会 {

        private static final Instant RECEIVED_ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant ADOPTED_ACQUIRED_AT = Instant.parse("2026-11-15T08:30:00Z");
        private static final Instant PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");
        private static final String RECEIVED_SUMMARY =
                "JPTYO;;2026-11-18T23:00:00.000000Z|USLAX;2026-12-01T22:00:00.000000Z;";
        private static final String ADOPTED_SUMMARY =
                "JPTYO;;2026-11-18T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;";

        private final UUID externalRecordId = UUID.randomUUID();

        private UndecidedRecordPage.Item item(UUID receivedRecordId, String recordType, boolean keyless) {
            return new UndecidedRecordPage.Item(
                    receivedRecordId,
                    ingestionId,
                    INGESTED_AT,
                    voyage.provider(),
                    recordType,
                    new PhysicalLineRange(3, 3),
                    Classification.QUARANTINED,
                    keyless ? null : "DEMO-201",
                    keyless ? null : "3",
                    "3 行目: 注記");
        }

        private ReceivedRecord received(Classification classification, Instant acquiredAt, String summaryOrNull) {
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecordId,
                    ingestionId,
                    PayloadHash.of(new byte[] {3}),
                    PayloadObjectKey.newRecordPayload(),
                    acquiredAt,
                    classification,
                    classification == Classification.ADOPTED ? null : "注記",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    summaryOrNull == null ? null : new ReceivedValue("DEMO-201", summaryOrNull));
        }

        private static VoyageValues voyage(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private AdoptedValue adoptedValue(UUID receivedRecordId) {
            return new AdoptedValue(
                    UUID.randomUUID(),
                    receivedRecordId,
                    TargetType.VOYAGE,
                    "DEMO-201",
                    ADOPTED_SUMMARY,
                    "3",
                    PUBLISHED_AT);
        }

        @Test
        void 行13_航海の内容の違う原本は受け取った値と取得時刻とこの外部版の採用値と情報版と公開した時刻と採用値の受信記録の取得時刻を返す() {
            ReceivedRecord quarantine = received(Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, RECEIVED_SUMMARY);
            ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, ADOPTED_SUMMARY);
            AdoptedValue adopted = adoptedValue(adoptedRecord.id());
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
            given(adoptedValues.currentOf(externalRecordId)).willReturn(Optional.of(adopted));

            Optional<UndecidedRecordValues> values =
                    service.undecidedRecordValues(item(quarantine.id(), "VOYAGE", false));

            assertThat(values)
                    .contains(new UndecidedRecordValues(
                            voyage("2026-12-01T22:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            new UndecidedRecordValues.Adopted(
                                    voyage("2026-12-01T14:00:00Z"), "3", PUBLISHED_AT, ADOPTED_ACQUIRED_AT)));
            then(receivedRecords).should(times(1)).findById(quarantine.id());
            then(adoptedValues).should(times(1)).currentOf(externalRecordId);
            then(receivedRecords).should(times(1)).findById(adoptedRecord.id());
            then(receivedRecords).shouldHaveNoMoreInteractions();
            then(ingestions).shouldHaveNoInteractions();
            then(dataSources).shouldHaveNoInteractions();
        }

        @Test
        void 行13_この外部版の採用値がなければ採用値の受信記録を引かず採用値は空() {
            ReceivedRecord quarantine = received(Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, RECEIVED_SUMMARY);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(adoptedValues.currentOf(externalRecordId)).willReturn(Optional.empty());

            Optional<UndecidedRecordValues> values =
                    service.undecidedRecordValues(item(quarantine.id(), "VOYAGE", false));

            assertThat(values)
                    .contains(new UndecidedRecordValues(voyage("2026-12-01T22:00:00Z"), RECEIVED_ACQUIRED_AT, null));
            then(receivedRecords).should(times(1)).findById(any());
            then(adoptedValues).should(times(1)).currentOf(externalRecordId);
        }

        @Test
        void 行10_値の列のない隔離は受け取った値を空にして取得時刻とこの外部版の採用値を返す() {
            ReceivedRecord quarantine = received(Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, null);
            ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, ADOPTED_SUMMARY);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
            given(adoptedValues.currentOf(externalRecordId)).willReturn(Optional.of(adoptedValue(adoptedRecord.id())));

            UndecidedRecordValues values = service.undecidedRecordValues(item(quarantine.id(), "VOYAGE", false))
                    .orElseThrow();

            assertThat(values.received()).isNull();
            assertThat(values.receivedAcquiredAt()).isEqualTo(RECEIVED_ACQUIRED_AT);
            assertThat(values.adopted().values()).isEqualTo(voyage("2026-12-01T14:00:00Z"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"キーのない隔離（形式の誤り）, VOYAGE, true"})
        void 行12_キーのない隔離は受信記録も採用値も引かず空(String name, String recordType, boolean keyless) {
            assertThat(service.undecidedRecordValues(item(UUID.randomUUID(), recordType, keyless)))
                    .isEmpty();
            then(receivedRecords).shouldHaveNoInteractions();
            then(adoptedValues).shouldHaveNoInteractions();
            then(ingestions).shouldHaveNoInteractions();
            then(dataSources).shouldHaveNoInteractions();
        }

        @Test
        void 行12_ない受信記録と隔離でない受信記録は採用値を引かず空() {
            ReceivedRecord duplicate = received(Classification.DUPLICATE, RECEIVED_ACQUIRED_AT, RECEIVED_SUMMARY);
            UUID missing = UUID.randomUUID();
            given(receivedRecords.findById(duplicate.id())).willReturn(Optional.of(duplicate));
            given(receivedRecords.findById(missing)).willReturn(Optional.empty());

            assertThat(service.undecidedRecordValues(item(duplicate.id(), "VOYAGE", false)))
                    .isEmpty();
            assertThat(service.undecidedRecordValues(item(missing, "VOYAGE", false)))
                    .isEmpty();
            then(adoptedValues).shouldHaveNoInteractions();
        }

        @Test
        void 行13_一覧の照会はこの外部版の採用値を引かない() {
            givenUndecided(null, 3);

            service.page(null, 1);

            then(adoptedValues).shouldHaveNoInteractions();
            then(receivedRecords).should(never()).findById(any());
        }
    }

    /**
     * Bolt 39d3c の行 13・23（主要実績の値と読めない値の照会。開始準備の決定 (4)、【業務の判断】(k)、T-236・T-244）。受け取った値は判断の画面の
     * 中身の原本種別で、採用値は採用値の対象の種類で型で分ける入口を呼ぶ。入口の {@link IllegalArgumentException} だけを受けて、その側を読めない
     * 印にし、WARN のログに ID・原本種別（制御文字を置き換える）・例外の型だけを残す。値の列がないことを読めないことより先に判定する。
     */
    @Nested
    class Bolt39d3c_主要実績と読めない値の照会 {

        private static final Instant RECEIVED_ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant ADOPTED_ACQUIRED_AT = Instant.parse("2026-11-15T08:30:00Z");
        private static final Instant PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");
        private static final String TRACKING_NUMBER = "CTABCDEFGH2345";
        private static final String VOYAGE_NUMBER = "DEMO-201";
        /** 壊れた要約（例外の文に入るので、ログに出ないことを確かめる印）。 */
        private static final String BROKEN = "BROKEN-SUMMARY-39D3C";

        private final UUID externalRecordId = UUID.randomUUID();
        private final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UndecidedRecordQueryService.class);
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();

        @BeforeEach
        void captureLogs() {
            logs.start();
            logger.addAppender(logs);
        }

        @org.junit.jupiter.api.AfterEach
        void releaseLogs() {
            logger.detachAppender(logs);
        }

        private UndecidedRecordPage.Item item(UUID receivedRecordId, String recordType) {
            return new UndecidedRecordPage.Item(
                    receivedRecordId,
                    ingestionId,
                    INGESTED_AT,
                    milestone.provider(),
                    recordType,
                    new PhysicalLineRange(3, 3),
                    Classification.QUARANTINED,
                    "MS-0201",
                    "2",
                    "3 行目: 注記");
        }

        private ReceivedRecord received(Classification classification, Instant acquiredAt, ReceivedValue value) {
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecordId,
                    ingestionId,
                    PayloadHash.of(new byte[] {3}),
                    PayloadObjectKey.newRecordPayload(),
                    acquiredAt,
                    classification,
                    classification == Classification.ADOPTED ? null : "注記",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    value);
        }

        private static String keyOf(String recordType) {
            return "MILESTONE".equals(recordType) ? TRACKING_NUMBER : VOYAGE_NUMBER;
        }

        /** 型ごとの読める要約（受け取った値と採用値で発生時刻・到着の時刻だけが違う。T-230）。 */
        private static String summaryOf(String recordType, boolean adopted) {
            String time = adopted ? "2026-11-19T00:00:00.000000Z" : "2026-11-19T01:00:00.000000Z";
            return "MILESTONE".equals(recordType)
                    ? "DEPARTURE;JPTYO;" + time
                    : "JPTYO;;2026-11-18T23:00:00.000000Z|USLAX;" + time + ";";
        }

        private static com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues valuesOf(
                String recordType, boolean adopted) {
            return com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues.ofSummary(
                    recordType, keyOf(recordType), summaryOf(recordType, adopted));
        }

        private AdoptedValue adoptedValue(UUID receivedRecordId, String targetType, String summary) {
            return new AdoptedValue(
                    UUID.randomUUID(),
                    receivedRecordId,
                    TargetType.valueOf(targetType),
                    keyOf(targetType),
                    summary,
                    "2",
                    PUBLISHED_AT);
        }

        /** 隔離と採用の受信記録と採用値を用意して照会する（採用値の要約が null なら採用値はない）。 */
        private UndecidedRecordValues query(
                String itemRecordType, ReceivedValue receivedValue, String adoptedTargetType, String adoptedSummary) {
            ReceivedRecord quarantine = received(Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, receivedValue);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            if (adoptedSummary == null) {
                given(adoptedValues.currentOf(externalRecordId)).willReturn(Optional.empty());
            } else {
                ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, null);
                given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
                given(adoptedValues.currentOf(externalRecordId))
                        .willReturn(Optional.of(adoptedValue(adoptedRecord.id(), adoptedTargetType, adoptedSummary)));
            }
            return service.undecidedRecordValues(item(quarantine.id(), itemRecordType))
                    .orElseThrow();
        }

        // --- 行 13: 主要実績の値と引く数 ---

        @Test
        void 行13_主要実績の内容の違う原本は航海と同じ数だけ引いて受け取った値とこの外部版の採用値を返す() {
            ReceivedRecord quarantine = received(
                    Classification.QUARANTINED,
                    RECEIVED_ACQUIRED_AT,
                    new ReceivedValue(TRACKING_NUMBER, summaryOf("MILESTONE", false)));
            ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, null);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
            given(adoptedValues.currentOf(externalRecordId))
                    .willReturn(
                            Optional.of(adoptedValue(adoptedRecord.id(), "MILESTONE", summaryOf("MILESTONE", true))));

            Optional<UndecidedRecordValues> values = service.undecidedRecordValues(item(quarantine.id(), "MILESTONE"));

            assertThat(values)
                    .contains(new UndecidedRecordValues(
                            valuesOf("MILESTONE", false),
                            RECEIVED_ACQUIRED_AT,
                            new UndecidedRecordValues.Adopted(
                                    valuesOf("MILESTONE", true), "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT)));
            then(receivedRecords).should(times(1)).findById(quarantine.id());
            then(adoptedValues).should(times(1)).currentOf(externalRecordId);
            then(receivedRecords).should(times(1)).findById(adoptedRecord.id());
            then(receivedRecords).shouldHaveNoMoreInteractions();
            then(ingestions).shouldHaveNoInteractions();
            then(dataSources).shouldHaveNoInteractions();
            assertThat(logs.list).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行13_読めない値でも引く数は同じ(String recordType) {
            ReceivedRecord quarantine = received(
                    Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, new ReceivedValue(keyOf(recordType), BROKEN));
            ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, null);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
            given(adoptedValues.currentOf(externalRecordId))
                    .willReturn(Optional.of(adoptedValue(adoptedRecord.id(), recordType, BROKEN)));

            service.undecidedRecordValues(item(quarantine.id(), recordType));

            then(receivedRecords).should(times(1)).findById(quarantine.id());
            then(adoptedValues).should(times(1)).currentOf(externalRecordId);
            then(receivedRecords).should(times(1)).findById(adoptedRecord.id());
            then(receivedRecords).shouldHaveNoMoreInteractions();
            then(ingestions).shouldHaveNoInteractions();
            then(dataSources).shouldHaveNoInteractions();
        }

        // --- 行 23: 読めない値（航海・主要実績 × 受け取った値・採用値・両側、知らない原本種別、組み合わせ） ---

        @ParameterizedTest(name = "{0} の受け取った値の要約が壊れている")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行23a_受け取った値が読めなければ受け取った値だけを読めない印にして採用値は示す(String recordType) {
            UndecidedRecordValues values = query(
                    recordType, new ReceivedValue(keyOf(recordType), BROKEN), recordType, summaryOf(recordType, true));

            assertThat(values)
                    .isEqualTo(new UndecidedRecordValues(
                            null,
                            true,
                            RECEIVED_ACQUIRED_AT,
                            new UndecidedRecordValues.Adopted(
                                    valuesOf(recordType, true), "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT)));
            assertThat(values.sameValues()).isFalse();
        }

        @ParameterizedTest(name = "{0} の採用値の要約が壊れている")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行23b_採用値が読めなければ採用値だけを読めない印にして情報版と公開した時刻と取得時刻は示す(String recordType) {
            UndecidedRecordValues values = query(
                    recordType, new ReceivedValue(keyOf(recordType), summaryOf(recordType, false)), recordType, BROKEN);

            assertThat(values)
                    .isEqualTo(new UndecidedRecordValues(
                            valuesOf(recordType, false),
                            false,
                            RECEIVED_ACQUIRED_AT,
                            new UndecidedRecordValues.Adopted(null, true, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT)));
            assertThat(values.sameValues()).isFalse();
        }

        @ParameterizedTest(name = "{0} の両側の要約が壊れている")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行23ab_両側が読めなければ両側を読めない印にする(String recordType) {
            UndecidedRecordValues values =
                    query(recordType, new ReceivedValue(keyOf(recordType), BROKEN), recordType, BROKEN);

            assertThat(values)
                    .isEqualTo(new UndecidedRecordValues(
                            null,
                            true,
                            RECEIVED_ACQUIRED_AT,
                            new UndecidedRecordValues.Adopted(null, true, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT)));
            assertThat(logs.list).hasSize(2);
        }

        @ParameterizedTest(name = "採用値の対象は {0}")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行23c_知らない原本種別は受け取った値だけが読めず採用値は対象の種類で読む(String adoptedTargetType) {
            UndecidedRecordValues values = query(
                    "CONTAINER",
                    new ReceivedValue(keyOf(adoptedTargetType), summaryOf(adoptedTargetType, false)),
                    adoptedTargetType,
                    summaryOf(adoptedTargetType, true));

            assertThat(values.received()).isNull();
            assertThat(values.receivedIllegible()).isTrue();
            assertThat(values.adopted().illegible()).isFalse();
            assertThat(values.adopted().values()).isEqualTo(valuesOf(adoptedTargetType, true));
        }

        @Test
        void 行23_値の列がないことを知らない原本種別より先に判定し読めない印を立てない() {
            UndecidedRecordValues values = query("CONTAINER", null, "VOYAGE", summaryOf("VOYAGE", true));

            assertThat(values.received()).isNull();
            assertThat(values.receivedIllegible()).isFalse();
            assertThat(values.adopted().values()).isEqualTo(valuesOf("VOYAGE", true));
            assertThat(logs.list).isEmpty();
        }

        @Test
        void 行23_値の列がなく採用値が読めなければ受け取った値は値の列のない形で採用値だけが読めない() {
            UndecidedRecordValues values = query("MILESTONE", null, "MILESTONE", BROKEN);

            assertThat(values.received()).isNull();
            assertThat(values.receivedIllegible()).isFalse();
            assertThat(values.adopted().illegible()).isTrue();
            assertThat(values.adopted().values()).isNull();
        }

        @Test
        void 行23_受け取った値が読めず採用値がなければ受け取った値だけを読めない印にする() {
            UndecidedRecordValues values =
                    query("MILESTONE", new ReceivedValue(TRACKING_NUMBER, BROKEN), "MILESTONE", null);

            assertThat(values).isEqualTo(new UndecidedRecordValues(null, true, RECEIVED_ACQUIRED_AT, null));
        }

        // --- 行 23: WARN のログ（ID・原本種別・例外の型だけ。要約の中身と例外の文は書かない） ---

        /**
         * 航海の入口の例外の文は壊れた要約を含む（{@code VoyageValues.ofSummary} の「寄港の要約は…: 」の後）ので、航海の組が「例外の文を書かない」
         * ことを確かめる（主要実績の例外の文は要約を含まないので、主要実績の組だけでは確かめられない。Bolt 39d3c の開発レビュー）。
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource({"VOYAGE", "MILESTONE"})
        void 行23_読めないときはWARNのログに受信記録IDと原本種別と例外の型だけを残す(String recordType) {
            ReceivedRecord quarantine = received(
                    Classification.QUARANTINED, RECEIVED_ACQUIRED_AT, new ReceivedValue(keyOf(recordType), BROKEN));
            ReceivedRecord adoptedRecord = received(Classification.ADOPTED, ADOPTED_ACQUIRED_AT, null);
            AdoptedValue adopted = adoptedValue(adoptedRecord.id(), recordType, BROKEN);
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(receivedRecords.findById(adoptedRecord.id())).willReturn(Optional.of(adoptedRecord));
            given(adoptedValues.currentOf(externalRecordId)).willReturn(Optional.of(adopted));

            service.undecidedRecordValues(item(quarantine.id(), recordType));

            assertThat(logs.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains(recordType)
                        .contains(IllegalArgumentException.class.getName())
                        .doesNotContain(BROKEN);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(logs.list.get(0).getFormattedMessage())
                    .contains(quarantine.id().toString());
            assertThat(logs.list.get(1).getFormattedMessage())
                    .contains(adopted.id().toString())
                    .contains(adoptedRecord.id().toString());
        }

        /** 改行・復帰・ベルに加え、行の区切り（U+2028）・段落の区切り（U+2029）・書式の文字（U+202E）も置き換える（前例 DisplayNameRule）。 */
        @Test
        void 行23_知らない原本種別の制御文字は置き換えてログに書く() {
            query(
                    "CON\nTAI\rNER\u0007X\u2028Y\u2029Z\u202E",
                    new ReceivedValue(VOYAGE_NUMBER, summaryOf("VOYAGE", false)),
                    "VOYAGE",
                    null);

            assertThat(logs.list)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("CON_TAI_NER_X_Y_Z_")
                            .doesNotContain("\n")
                            .doesNotContain("\r")
                            .doesNotContain("\u0007")
                            .doesNotContain("\u2028")
                            .doesNotContain("\u2029")
                            .doesNotContain("\u202E"));
        }

        @Test
        void 行23_リポジトリの不正な引数の例外は読めない値にせず上げる() {
            UUID id = UUID.randomUUID();
            given(receivedRecords.findById(id)).willThrow(new IllegalArgumentException("リポジトリの誤り"));
            UndecidedRecordPage.Item quarantine = item(id, "MILESTONE");

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.undecidedRecordValues(quarantine))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("リポジトリの誤り");
        }

        // --- 行 23: UndecidedRecordValues の不変条件（T-244） ---

        @Test
        void 行23_読めない印と値の両方を持つ受け取った値は作れない() {
            com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues values =
                    valuesOf("MILESTONE", false);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> new UndecidedRecordValues(values, true, RECEIVED_ACQUIRED_AT, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new UndecidedRecordValues(null, true, RECEIVED_ACQUIRED_AT, null).receivedIllegible())
                    .isTrue();
            assertThat(new UndecidedRecordValues(null, false, RECEIVED_ACQUIRED_AT, null).receivedIllegible())
                    .isFalse();
        }

        @Test
        void 行23_採用値は読めないときだけ値がない() {
            com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues values =
                    valuesOf("MILESTONE", true);

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            new UndecidedRecordValues.Adopted(values, true, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            new UndecidedRecordValues.Adopted(null, false, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new UndecidedRecordValues.Adopted(null, true, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT).values())
                    .isNull();
            assertThat(new UndecidedRecordValues.Adopted(values, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT).illegible())
                    .isFalse();
        }
    }

    /**
     * Bolt 39h2 の行 8・4（差異の判断の画面の中身。決定 (5)）。差異は受け取った値だけを返し、この外部版の採用値は引かずに空にする（差異の外部原本は採用値を
     * 持たない。比べる相手がないのに「この外部版の採用値はありません。」と出さない）。一覧の行は分類を持つ。
     */
    @Nested
    class Bolt39h2_差異の受け取った値 {

        private static final Instant ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final String SUMMARY = "JPTYO;;2026-11-18T23:00:00.000000Z|USLAX;2026-12-01T22:00:00.000000Z;";

        private final UUID externalRecordId = UUID.randomUUID();

        private ReceivedRecord discrepancy() {
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecordId,
                    ingestionId,
                    PayloadHash.of(new byte[] {3}),
                    PayloadObjectKey.newRecordPayload(),
                    ACQUIRED_AT,
                    Classification.DISCREPANCY,
                    "3 行目: 版の逆転",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    new ReceivedValue("DEMO-201", SUMMARY));
        }

        private UndecidedRecordPage.Item item(UUID receivedRecordId) {
            return new UndecidedRecordPage.Item(
                    receivedRecordId,
                    ingestionId,
                    INGESTED_AT,
                    voyage.provider(),
                    "VOYAGE",
                    new PhysicalLineRange(3, 3),
                    Classification.DISCREPANCY,
                    "DEMO-201",
                    "3",
                    "3 行目: 版の逆転");
        }

        @Test
        void 行8_差異は受け取った値と取得時刻を返しこの外部版の採用値は引かずに空() {
            ReceivedRecord discrepancy = discrepancy();
            given(receivedRecords.findById(discrepancy.id())).willReturn(Optional.of(discrepancy));

            Optional<UndecidedRecordValues> values = service.undecidedRecordValues(item(discrepancy.id()));

            assertThat(values).hasValueSatisfying(found -> {
                assertThat(found.received()).isNotNull();
                assertThat(found.receivedAcquiredAt()).isEqualTo(ACQUIRED_AT);
                assertThat(found.adopted()).isNull();
            });
            // 差異は「この外部版の採用値」を引かない。この外部 ID の採用値の一覧だけ引く（Bolt 39h3 の決定 (4)。期待が変わる既存の組）
            then(adoptedValues).should(never()).currentOf(any());
            then(adoptedValues).should(times(1)).findByExternalId(voyage.provider(), "VOYAGE", "DEMO-201");
        }

        @Test
        void 行4_差異の項目は分類を持ち外部原本を持つのでキーのない隔離ではない() {
            UndecidedRecord waiting = new UndecidedRecord(
                    UUID.randomUUID(),
                    Classification.DISCREPANCY,
                    ingestionId,
                    INGESTED_AT,
                    voyage.id(),
                    new PhysicalLineRange(3, 3),
                    "DEMO-201",
                    "3",
                    "3 行目: 版の逆転");
            given(receivedRecords.undecidedRecord(waiting.receivedRecordId())).willReturn(Optional.of(waiting));

            UndecidedRecordPage.Item found =
                    service.undecidedRecord(waiting.receivedRecordId()).orElseThrow();

            assertThat(found.classification()).isEqualTo(Classification.DISCREPANCY);
            assertThat(found.keyless()).isFalse();
        }
    }

    /**
     * Bolt 39h3 の行 3（判断の画面の「この外部 ID の採用値」と古い外部版の隔離の警告の照会。開始準備の決定 (3)・(4)・(6)、台帳 D-150・D-151（仮））。
     * 見本は外部 ID DEMO-201（提供元は航海の情報源）、外部版「1」「2」、取得時刻 G1 &lt; G1′ &lt; G2、公開した時刻 P1 &lt; P2 &lt; P3。メモリ実装で
     * 実際の状態を作り、引く回数は Mockito の呼び出しの記録で縛る（変異 M5〜M7b・M13）。
     */
    @Nested
    class Bolt39h3_この外部IDの採用値と古い外部版 {

        private static final Instant G0 = Instant.parse("2026-11-09T08:00:00Z");
        private static final Instant G1 = Instant.parse("2026-11-10T08:00:00Z");
        private static final Instant G1_NEXT = Instant.parse("2026-11-10T09:00:00Z");
        private static final Instant G2 = Instant.parse("2026-11-11T08:00:00Z");
        private static final Instant G2_NEXT = Instant.parse("2026-11-11T09:00:00Z");
        private static final Instant P1 = Instant.parse("2026-11-12T01:00:00Z");
        private static final Instant P2 = Instant.parse("2026-11-12T02:00:00Z");
        private static final Instant P3 = Instant.parse("2026-11-12T03:00:00Z");
        private static final String ARRIVAL_1 = "2026-12-01T10:00:00Z";
        private static final String ARRIVAL_2 = "2026-12-01T11:00:00Z";
        private static final String ARRIVAL_1_DIFFERENT = "2026-12-01T12:00:00Z";
        private static final String BROKEN = "BROKEN-SUMMARY-39H3";

        private final InMemoryIngestionRepository memoryIngestions = new InMemoryIngestionRepository();
        private final InMemoryExternalRecordRepository memoryExternalRecords = new InMemoryExternalRecordRepository();
        private final InMemoryReceivedRecordRepository memoryReceivedRecords = new InMemoryReceivedRecordRepository(
                memoryIngestions, memoryExternalRecords, new InMemoryReceivedRecordDecisionRepository());
        private final InMemoryAdoptedValueRepository memoryAdoptedValues =
                new InMemoryAdoptedValueRepository(memoryReceivedRecords);
        private final UndecidedRecordQueryService memoryService = new UndecidedRecordQueryService(
                memoryReceivedRecords, memoryIngestions, dataSources, memoryAdoptedValues);

        private static VoyageValues voyageArriving(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        /** 同じ冪等性キーの外部原本はあればそれを使う（同じ外部版の2つ目の受信記録は同じ外部原本に付く）。 */
        private ExternalRecord externalRecord(String provider, String externalVersion) {
            IdempotencyKey key = new IdempotencyKey(provider, "VOYAGE", "DEMO-201", externalVersion);
            return memoryExternalRecords.findByKey(key).orElseGet(() -> {
                ExternalRecord created = new ExternalRecord(
                        UUID.randomUUID(),
                        key,
                        PayloadHash.of(new byte[] {1}),
                        PayloadObjectKey.newRecordPayload(),
                        P1);
                memoryExternalRecords.add(created);
                return created;
            });
        }

        private ReceivedRecord stored(
                ExternalRecord externalRecord, Classification classification, Instant acquiredAt, String arrival) {
            ReceivedRecord received = new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecord.id(),
                    ingestionId,
                    PayloadHash.of(new byte[] {2}),
                    PayloadObjectKey.newRecordPayload(),
                    acquiredAt,
                    classification,
                    classification == Classification.ADOPTED ? null : "注記",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    ReceivedValue.of(voyageArriving(arrival)));
            memoryReceivedRecords.add(received);
            return received;
        }

        /** 採用した外部原本（採用値と、取得時刻を持つ採用の受信記録）を作る。 */
        private void adopt(String externalVersion, Instant acquiredAt, Instant publishedAt, String arrival) {
            adopt(
                    voyage.provider(),
                    externalVersion,
                    acquiredAt,
                    publishedAt,
                    voyageArriving(arrival).summary());
        }

        private void adopt(
                String provider, String externalVersion, Instant acquiredAt, Instant publishedAt, String summary) {
            ReceivedRecord adopted =
                    stored(externalRecord(provider, externalVersion), Classification.ADOPTED, acquiredAt, ARRIVAL_1);
            memoryAdoptedValues.add(new AdoptedValue(
                    UUID.randomUUID(),
                    adopted.id(),
                    TargetType.VOYAGE,
                    "DEMO-201",
                    summary,
                    externalVersion,
                    publishedAt));
        }

        /** 判断を待つ受信記録（外部版の外部原本は採用済みの外部原本と同じ外部版なら同じものを使う）。 */
        private UndecidedRecordPage.Item undecided(
                String externalVersion, Classification classification, Instant acquiredAt, String arrival) {
            ExternalRecord externalRecord = memoryExternalRecords
                    .findByKey(new IdempotencyKey(voyage.provider(), "VOYAGE", "DEMO-201", externalVersion))
                    .orElseGet(() -> externalRecord(voyage.provider(), externalVersion));
            ReceivedRecord received = stored(externalRecord, classification, acquiredAt, arrival);
            return new UndecidedRecordPage.Item(
                    received.id(),
                    ingestionId,
                    INGESTED_AT,
                    voyage.provider(),
                    "VOYAGE",
                    new PhysicalLineRange(3, 3),
                    classification,
                    "DEMO-201",
                    externalVersion,
                    "注記");
        }

        private static UndecidedRecordValues.Adopted adopted(
                String arrival, String externalVersion, Instant publishedAt, Instant acquiredAt) {
            return new UndecidedRecordValues.Adopted(voyageArriving(arrival), externalVersion, publishedAt, acquiredAt);
        }

        private UndecidedRecordValues valuesOf(UndecidedRecordPage.Item item) {
            return memoryService.undecidedRecordValues(item).orElseThrow();
        }

        /** 見本の状況: 外部版「1」を G1 に採用（P1）、外部版「2」を G2 に採用（P2）。 */
        private void givenV1AndV2Adopted() {
            adopt("1", G1, P1, ARRIVAL_1);
            adopt("2", G2, P2, ARRIVAL_2);
        }

        @Test
        void 行3_a_V2の取込の後のV1の隔離はこの外部IDの採用値が最後に取得したV2で古い外部版の隔離は真() {
            givenV1AndV2Adopted();

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G1_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_2, "2", P2, G2));
            assertThat(values.olderThanAdopted()).isTrue();
            // この外部版の採用値（V1）は今のまま
            assertThat(values.adopted()).isEqualTo(adopted(ARRIVAL_1, "1", P1, G1));
        }

        @Test
        void 行3_b_隔離の取得時刻がV2より後なら比べる相手は公開した時刻が最も新しい採用値で古い外部版の隔離は偽() {
            givenV1AndV2Adopted();

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G2_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_2, "2", P2, G2));
            assertThat(values.olderThanAdopted()).isFalse();
        }

        @Test
        void 行3_c_今使っている採用値が同じ外部版で逆転でもなければ領域なしで偽() {
            adopt("1", G1, P1, ARRIVAL_1);

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G1_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isNull();
            assertThat(values.olderThanAdopted()).isFalse();
            assertThat(values.adopted()).isEqualTo(adopted(ARRIVAL_1, "1", P1, G1));
        }

        @Test
        void 行3_d_この外部IDの採用値がなければ領域なしで偽() {
            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G1_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isNull();
            assertThat(values.olderThanAdopted()).isFalse();
        }

        @Test
        void 行3_e_差異の領域は逆転なので最後に取得した採用値で古い外部版の隔離は偽() {
            givenV1AndV2Adopted();

            UndecidedRecordValues values = valuesOf(undecided("0", Classification.DISCREPANCY, G0, ARRIVAL_1));

            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_2, "2", P2, G2));
            assertThat(values.olderThanAdopted()).isFalse();
            assertThat(values.adopted()).isNull();
        }

        @Test
        void 行3_g_比べる相手の値を読めなければ読めない印にして外部版と時刻は示し500にしない() {
            adopt("1", G1, P1, ARRIVAL_1);
            adopt(voyage.provider(), "2", G2, P2, BROKEN);

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G1_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isEqualTo(new UndecidedRecordValues.Adopted(null, true, "2", P2, G2));
            assertThat(values.olderThanAdopted()).isTrue();
        }

        @Test
        void 行3_h_同じ外部IDの別の提供元の採用値は比べる相手にしない() {
            adopt("1", G1, P1, ARRIVAL_1);
            adopt("別の提供元", "2", G2, P2, voyageArriving(ARRIVAL_2).summary());

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G1_NEXT, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isNull();
            assertThat(values.olderThanAdopted()).isFalse();
        }

        @Test
        void 行3_i_V1の隔離を採用した後のV1の隔離は今使っている採用値が同じ外部版でも逆転なら警告ありで領域はV2() {
            givenV1AndV2Adopted();
            // V1′ を採用した（取得 G1′、公開 P3）。今使っている採用値は V1′（外部版「1」）、最後に取得したのは V2
            adopt("1", G1_NEXT, P3, ARRIVAL_1_DIFFERENT);

            UndecidedRecordValues values = valuesOf(undecided(
                    "1", Classification.QUARANTINED, Instant.parse("2026-11-10T10:00:00Z"), "2026-12-01T13:00:00Z"));

            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_2, "2", P2, G2));
            assertThat(values.olderThanAdopted()).isTrue();
        }

        @Test
        void 行3_j_同じ外部版で取得時刻が前なら逆転なので領域を出し警告も出す() {
            adopt("1", G1, P1, ARRIVAL_1);

            UndecidedRecordValues values =
                    valuesOf(undecided("1", Classification.QUARANTINED, G0, ARRIVAL_1_DIFFERENT));

            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_1, "1", P1, G1));
            assertThat(values.olderThanAdopted()).isTrue();
        }

        @Test
        void 行3_引く数はこの外部IDの採用値の一覧1回と比べる相手の採用値1回で受信記録は引き足さない() {
            ReceivedRecord quarantine = new ReceivedRecord(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ingestionId,
                    PayloadHash.of(new byte[] {3}),
                    PayloadObjectKey.newRecordPayload(),
                    G1_NEXT,
                    Classification.QUARANTINED,
                    "注記",
                    INGESTED_AT,
                    new PhysicalLineRange(3, 3),
                    ReceivedValue.of(voyageArriving(ARRIVAL_1_DIFFERENT)));
            UndecidedRecordPage.Item item = new UndecidedRecordPage.Item(
                    quarantine.id(),
                    ingestionId,
                    INGESTED_AT,
                    voyage.provider(),
                    "VOYAGE",
                    new PhysicalLineRange(3, 3),
                    Classification.QUARANTINED,
                    "DEMO-201",
                    "1",
                    "注記");
            ExternalIdAdoptedValue v2 = new ExternalIdAdoptedValue("2", "DEMO-201", G2, P2, UUID.randomUUID());
            given(receivedRecords.findById(quarantine.id())).willReturn(Optional.of(quarantine));
            given(adoptedValues.currentOf(quarantine.externalRecordId())).willReturn(Optional.empty());
            given(adoptedValues.findByExternalId(voyage.provider(), "VOYAGE", "DEMO-201"))
                    .willReturn(List.of(v2));
            given(adoptedValues.findByReceivedRecordId(v2.receivedRecordId()))
                    .willReturn(Optional.of(new AdoptedValue(
                            UUID.randomUUID(),
                            v2.receivedRecordId(),
                            TargetType.VOYAGE,
                            "DEMO-201",
                            voyageArriving(ARRIVAL_2).summary(),
                            "2",
                            P2)));

            UndecidedRecordValues values = service.undecidedRecordValues(item).orElseThrow();

            assertThat(values.olderThanAdopted()).isTrue();
            assertThat(values.partner()).isEqualTo(adopted(ARRIVAL_2, "2", P2, G2));
            then(adoptedValues).should(times(1)).findByExternalId(voyage.provider(), "VOYAGE", "DEMO-201");
            then(adoptedValues).should(times(1)).findByReceivedRecordId(v2.receivedRecordId());
            then(adoptedValues).should(times(1)).currentOf(quarantine.externalRecordId());
            then(receivedRecords).should(times(1)).findById(quarantine.id());
            then(receivedRecords).shouldHaveNoMoreInteractions();
        }

        @Test
        void 行3_f_キーのない隔離は空で一覧の照会もこの外部IDの採用値を引かない() {
            givenUndecided(null, 3);

            service.page(null, 1);
            assertThat(service.undecidedRecordValues(new UndecidedRecordPage.Item(
                            UUID.randomUUID(),
                            ingestionId,
                            INGESTED_AT,
                            voyage.provider(),
                            "VOYAGE",
                            null,
                            Classification.QUARANTINED,
                            null,
                            null,
                            "ファイル: 注記")))
                    .isEmpty();

            then(adoptedValues).shouldHaveNoInteractions();
        }
    }

    private Ingestion ingestion(UUID id) {
        return new Ingestion(
                id,
                voyage.id(),
                PayloadObjectKey.newIngestionFile(),
                PayloadHash.of(new byte[] {1}),
                new UserId(UUID.randomUUID()),
                INGESTED_AT,
                2,
                null,
                0);
    }
}
