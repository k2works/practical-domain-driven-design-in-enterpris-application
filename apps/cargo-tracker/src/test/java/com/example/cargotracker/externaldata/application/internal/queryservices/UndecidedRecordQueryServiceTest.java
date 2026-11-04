package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedQuarantine;
import com.example.cargotracker.shared.domain.UserId;
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
    private final UndecidedRecordQueryService service =
            new UndecidedRecordQueryService(receivedRecords, ingestions, dataSources);

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
    private UndecidedQuarantine keyless(DataSource source, int line) {
        return new UndecidedQuarantine(
                UUID.randomUUID(),
                ingestionId,
                INGESTED_AT,
                source.id(),
                new PhysicalLineRange(line, line),
                null,
                null,
                line + " 行目: 値が空です（external_id）。提供元に確かめてください。");
    }

    private void givenUndecided(UUID filter, int total) {
        given(receivedRecords.countUndecidedQuarantines(filter)).willReturn(total);
        given(receivedRecords.undecidedQuarantines(any(), anyInt(), anyInt())).willAnswer(invocation -> {
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
        UndecidedQuarantine quarantine = new UndecidedQuarantine(
                UUID.randomUUID(),
                ingestionId,
                INGESTED_AT,
                voyage.id(),
                new PhysicalLineRange(140, 141),
                externalId,
                externalVersion,
                "140 行目から 141 行目まで: 注記");
        given(receivedRecords.countUndecidedQuarantines(null)).willReturn(1);
        given(receivedRecords.undecidedQuarantines(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
                .willReturn(List.of(quarantine));

        UndecidedRecordPage.Item item = service.page(null, 1).items().getFirst();

        assertThat(item)
                .isEqualTo(new UndecidedRecordPage.Item(
                        quarantine.receivedRecordId(),
                        INGESTED_AT,
                        voyage.provider(),
                        "VOYAGE",
                        new PhysicalLineRange(140, 141),
                        keyless,
                        externalId,
                        externalVersion,
                        "140 行目から 141 行目まで: 注記"));
    }

    @Test
    void 行1_行2_期待の組は外部原本のある隔離とない隔離の両方を含む() {
        // 一覧に出る分類は隔離だけ（採用・重複は照会が返さない。契約のテストの行 3）。隔離の 2 つの種類をすべて縛る（T-240）
        UndecidedQuarantine withKey = new UndecidedQuarantine(
                UUID.randomUUID(), ingestionId, INGESTED_AT, voyage.id(), null, "V-1", "1", "注記");
        UndecidedQuarantine withoutKey = keyless(voyage, 2);
        given(receivedRecords.countUndecidedQuarantines(null)).willReturn(2);
        given(receivedRecords.undecidedQuarantines(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
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
            then(receivedRecords).should().undecidedQuarantines(null, offset, UndecidedRecordQueryService.PAGE_SIZE);
        }
    }

    // --- 行 8: 情報源の引き方 ---

    @Test
    void 行8_情報源はページの中の異なる情報源IDごとに1回だけ引き取込は引かない() {
        given(receivedRecords.countUndecidedQuarantines(null)).willReturn(4);
        given(receivedRecords.undecidedQuarantines(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
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
        given(receivedRecords.countUndecidedQuarantines(null)).willReturn(0);

        UndecidedRecordPage result = service.page(null, 1);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.ingestionFilter()).isNull();
        assertThat(result.ingestionNotFound()).isFalse();
        then(receivedRecords).should(never()).undecidedQuarantines(any(), anyInt(), anyInt());
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
        then(receivedRecords).should().undecidedQuarantines(ingestionId, 0, UndecidedRecordQueryService.PAGE_SIZE);
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
        then(receivedRecords).should(never()).countUndecidedQuarantines(missing);
        then(receivedRecords).should().undecidedQuarantines(null, 0, UndecidedRecordQueryService.PAGE_SIZE);
        then(ingestions).should(times(1)).findById(missing);
    }

    /** Bolt 39d2 の行 18（判断の画面の中身。開始準備の決定 (3)）。ID で絞った判断を待つ隔離を一覧と同じ項目にする。 */
    @Nested
    class Bolt39d2_判断の画面の中身 {

        @Test
        void 行18_IDで絞った判断を待つ隔離を一覧と同じ項目で返し情報源を1回引く() {
            UndecidedQuarantine mismatch = new UndecidedQuarantine(
                    UUID.randomUUID(),
                    ingestionId,
                    INGESTED_AT,
                    voyage.id(),
                    new PhysicalLineRange(140, 141),
                    "DEMO-201",
                    "3",
                    "140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。");
            given(receivedRecords.undecidedQuarantine(mismatch.receivedRecordId()))
                    .willReturn(Optional.of(mismatch));

            assertThat(service.undecidedQuarantine(mismatch.receivedRecordId()))
                    .contains(new UndecidedRecordPage.Item(
                            mismatch.receivedRecordId(),
                            INGESTED_AT,
                            voyage.provider(),
                            "VOYAGE",
                            new PhysicalLineRange(140, 141),
                            false,
                            "DEMO-201",
                            "3",
                            mismatch.note()));
            then(dataSources).should(times(1)).findById(voyage.id());
        }

        @Test
        void 行18_キーのない隔離は形式の誤りの印を持つ() {
            UndecidedQuarantine keyless = keyless(milestone, 4);
            given(receivedRecords.undecidedQuarantine(keyless.receivedRecordId()))
                    .willReturn(Optional.of(keyless));

            assertThat(service.undecidedQuarantine(keyless.receivedRecordId())).hasValueSatisfying(item -> {
                assertThat(item.keyless()).isTrue();
                assertThat(item.recordType()).isEqualTo("MILESTONE");
            });
        }

        @Test
        void 行5_判断を待つ隔離でなければ空で情報源を引かない() {
            UUID decided = UUID.randomUUID();
            given(receivedRecords.undecidedQuarantine(decided)).willReturn(Optional.empty());

            assertThat(service.undecidedQuarantine(decided)).isEmpty();
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
            given(receivedRecords.countUndecidedQuarantines(ingestionId)).willReturn(0);

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
            given(receivedRecords.countUndecidedQuarantines(null)).willReturn(101, 100);
            given(receivedRecords.undecidedQuarantines(null, 100, UndecidedRecordQueryService.PAGE_SIZE))
                    .willReturn(List.of());
            given(receivedRecords.undecidedQuarantines(null, 0, UndecidedRecordQueryService.PAGE_SIZE))
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
            then(receivedRecords).should(times(2)).countUndecidedQuarantines(null);
            then(receivedRecords).should(times(2)).undecidedQuarantines(any(), anyInt(), anyInt());
        }

        @Test
        void 行15_読んだページが空でなければ数え直さない() {
            givenUndecided(null, 101);

            service.page(null, 2);

            then(receivedRecords).should(times(1)).countUndecidedQuarantines(null);
            then(receivedRecords).should(times(1)).undecidedQuarantines(any(), anyInt(), anyInt());
        }

        @Test
        void 行16_数え直して0件なら読み直さず0件のページを返す() {
            given(ingestions.findById(ingestionId)).willReturn(Optional.of(ingestion(ingestionId)));
            given(receivedRecords.countUndecidedQuarantines(ingestionId)).willReturn(1, 0);
            given(receivedRecords.undecidedQuarantines(any(), anyInt(), anyInt()))
                    .willReturn(List.of());

            UndecidedRecordPage result = service.page(ingestionId, 1);

            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.pageCount()).isZero();
            assertThat(result.ingestionFilter()).isEqualTo(ingestionId);
            assertThat(result.keylessQuarantineCount()).isZero();
            then(receivedRecords).should(times(1)).undecidedQuarantines(any(), anyInt(), anyInt());
            then(receivedRecords).should(never()).countUndecidedKeylessQuarantines(any());
        }

        @Test
        void 行16_読み直しても空なら0件のページを返す() {
            given(receivedRecords.countUndecidedQuarantines(null)).willReturn(201, 150);
            given(receivedRecords.undecidedQuarantines(any(), anyInt(), anyInt()))
                    .willReturn(List.of());

            UndecidedRecordPage result = service.page(null, 3);

            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(result.firstShown()).isZero();
            assertThat(result.lastShown()).isZero();
            then(receivedRecords).should().undecidedQuarantines(null, 200, UndecidedRecordQueryService.PAGE_SIZE);
            then(receivedRecords).should().undecidedQuarantines(null, 100, UndecidedRecordQueryService.PAGE_SIZE);
            then(receivedRecords).should(times(2)).countUndecidedQuarantines(null);
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
