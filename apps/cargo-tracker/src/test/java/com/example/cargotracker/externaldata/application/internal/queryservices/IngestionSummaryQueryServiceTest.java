package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
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
 * 取込の要約の照会（Bolt 39c3 の行 16・17。開始準備の決定 (4)、T-236・T-244）。ポートを mock にし、隔離の注記を最初の
 * {@link IngestionSummaryQueryService#SHOWN_QUARANTINE_LIMIT} 件だけ読み、残りの件数を隔離の件数から引くこと、隔離が 0 件なら照会を
 * 呼ばないこと（呼び出しの記録）を確かめる。要確認の復元（Bolt 39c4 の行 6・7）は入れ子のクラス。
 */
class IngestionSummaryQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T01:05:00Z");

    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
    private final ReceivedRecordRepository receivedRecords = mock(ReceivedRecordRepository.class);
    private final IngestionSummaryQueryService service =
            new IngestionSummaryQueryService(ingestions, dataSources, receivedRecords);

    private final DataSource source =
            new DataSource(UUID.randomUUID(), "照会の提供元-" + UUID.randomUUID(), "VOYAGE", ApprovalStatus.APPROVED);
    private final Ingestion ingestion = new Ingestion(
            UUID.randomUUID(),
            source.id(),
            PayloadObjectKey.newIngestionFile(),
            PayloadHash.of("ファイル".getBytes(StandardCharsets.UTF_8)),
            new UserId(UUID.randomUUID()),
            NOW,
            1,
            null,
            0);

    @BeforeEach
    void 取込と情報源がある() {
        given(ingestions.findById(ingestion.id())).willReturn(Optional.of(ingestion));
        given(dataSources.findById(source.id())).willReturn(Optional.of(source));
    }

    private void givenQuarantined(int count) {
        given(receivedRecords.countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                .willReturn(count);
        given(receivedRecords.quarantinedOf(ingestion.id(), IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT))
                .willReturn(
                        IntStream.rangeClosed(1, Math.min(count, IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT))
                                .mapToObj(this::quarantined)
                                .toList());
    }

    /** 物理行の (n + 1) 行目の行の誤りの隔離（注記は行の順が分かる文）。 */
    private ReceivedRecord quarantined(int n) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                null,
                ingestion.id(),
                PayloadHash.of(new byte[] {(byte) n}),
                PayloadObjectKey.newRecordPayload(),
                null,
                Classification.QUARANTINED,
                (n + 1) + " 行目: 値が空です（external_id）。提供元に確かめてください。",
                NOW,
                new PhysicalLineRange(n + 1, n + 1),
                null);
    }

    @ParameterizedTest(name = "隔離 {0} 件は {1} 件を示し残り {2} 件")
    @CsvSource({"1, 1, 0", "100, 100, 0", "101, 100, 1", "10000, 100, 9900"})
    void 行16_隔離の注記は照会の順に最初の100件を示し残りの件数は隔離の件数から示した数を引く(int quarantinedCount, int shown, int remaining) {
        givenQuarantined(quarantinedCount);

        IngestionSummaryView summary = service.summaryOf(ingestion.id()).orElseThrow();

        assertThat(summary.quarantinedCount()).isEqualTo(quarantinedCount);
        assertThat(summary.quarantineNotes())
                .hasSize(shown)
                .startsWith("2 行目: 値が空です（external_id）。提供元に確かめてください。")
                .endsWith((shown + 1) + " 行目: 値が空です（external_id）。提供元に確かめてください。");
        assertThat(summary.remainingQuarantineCount()).isEqualTo(remaining);
        then(receivedRecords)
                .should()
                .quarantinedOf(ingestion.id(), IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT);
    }

    @Test
    void 行16_示す上限は100件() {
        assertThat(IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT).isEqualTo(100);
    }

    @Test
    void 行17_隔離が0件なら隔離の照会を呼ばず注記はなく残りは0() {
        given(receivedRecords.countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                .willReturn(0);

        IngestionSummaryView summary = service.summaryOf(ingestion.id()).orElseThrow();

        assertThat(summary.quarantineNotes()).isEmpty();
        assertThat(summary.remainingQuarantineCount()).isZero();
        then(receivedRecords).should(never()).quarantinedOf(any(), anyInt());
    }

    /**
     * Bolt 39h の行 8（S-16 の差異の件数と理由。開始準備の決定 (7)）。差異の件数を数え、隔離・差異の注記は照会の順に最初の 100 件、残りは隔離と
     * 差異の件数の和から示した数を引く。隔離も差異も 0 件なら照会を呼ばない。
     */
    @Nested
    class Bolt39h_差異 {

        private void givenCounts(int quarantined, int discrepancy) {
            given(receivedRecords.countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                    .willReturn(quarantined);
            given(receivedRecords.countByIngestionAndClassification(ingestion.id(), Classification.DISCREPANCY))
                    .willReturn(discrepancy);
            int listed = Math.min(quarantined + discrepancy, IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT);
            given(receivedRecords.quarantinedOf(ingestion.id(), IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT))
                    .willReturn(IntStream.rangeClosed(1, listed)
                            .mapToObj(IngestionSummaryQueryServiceTest.this::quarantined)
                            .toList());
        }

        @ParameterizedTest(name = "隔離 {0}・差異 {1} は {2} 件を示し残り {3} 件")
        @CsvSource({"1, 1, 2, 0", "0, 1, 1, 0", "1, 100, 100, 1", "0, 0, 0, 0"})
        void 行8_差異の件数を数え隔離と差異の注記を最初の100件だけ示し残りは隔離と差異の和から引く(int quarantined, int discrepancy, int shown, int remaining) {
            givenCounts(quarantined, discrepancy);

            IngestionSummaryView summary = service.summaryOf(ingestion.id()).orElseThrow();

            assertThat(summary.quarantinedCount()).isEqualTo(quarantined);
            assertThat(summary.discrepancyCount()).isEqualTo(discrepancy);
            assertThat(summary.quarantineNotes()).hasSize(shown);
            assertThat(summary.remainingQuarantineCount()).isEqualTo(remaining);
            if (quarantined + discrepancy == 0) {
                then(receivedRecords).should(never()).quarantinedOf(any(), anyInt());
            }
        }
    }

    /**
     * Bolt 39c4 の行 6・7（要確認の復元。開始準備の決定 (2)・(3)）。取込の取り込めなかった行（始まり・数）と、読んだ隔離の受信記録にファイル
     * 全体の原本のキーを持つものがあるかの組から、取り込めなかった部分の値を組み立てる。39c3 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c4_要確認 {

        /** 取り込めなかった行を持つ取込（始まりと数は表の CHECK と同じ組）。 */
        private Ingestion ingestionWith(Integer unparsedFromLine, int unparsedLineCount) {
            Ingestion withUnparsed = new Ingestion(
                    UUID.randomUUID(),
                    source.id(),
                    PayloadObjectKey.newIngestionFile(),
                    PayloadHash.of("ファイル".getBytes(StandardCharsets.UTF_8)),
                    new UserId(UUID.randomUUID()),
                    NOW,
                    9,
                    unparsedFromLine,
                    unparsedLineCount);
            given(ingestions.findById(withUnparsed.id())).willReturn(Optional.of(withUnparsed));
            return withUnparsed;
        }

        /** ファイルの誤りの隔離（ファイル全体の原本のキーを持ち、範囲を持たない。IngestionService の取り込めなかった部分）。 */
        private ReceivedRecord fileDefect(Ingestion target) {
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    null,
                    target.id(),
                    target.fileHash(),
                    target.fileObjectKey(),
                    null,
                    Classification.QUARANTINED,
                    "ファイル: 必要な列がありません（port_calls）。選んだ情報源の種別に合ったファイルか確かめてください。合っていれば提供元に確かめてください。",
                    NOW,
                    null,
                    null);
        }

        /** 行の誤り、またはファイルの終わりまでの隔離（自分の原文のキーを持つ）。 */
        private ReceivedRecord ownPayload(Ingestion target, int fromLine, int lastLine) {
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    null,
                    target.id(),
                    PayloadHash.of(new byte[] {(byte) fromLine}),
                    PayloadObjectKey.newRecordPayload(),
                    null,
                    Classification.QUARANTINED,
                    fromLine + " 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。",
                    NOW,
                    new PhysicalLineRange(fromLine, lastLine),
                    null);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                nullValues = "NULL",
                value = {
                    "数 0・キーなし, NULL, 0, false, false, NULL, 0, false",
                    "数 0・キーあり, NULL, 0, true, true, NULL, 0, true",
                    "数あり・キーなし, 4, 2, false, true, 4, 2, false",
                    "数あり・キーあり, 2, 9, true, true, 2, 9, true"
                })
        void 行6_取込の取り込めなかった行とファイル全体の原本を指す隔離の有無の組から取り込めなかった部分を組み立てる(
                String label,
                Integer unparsedFromLine,
                int unparsedLineCount,
                boolean fileKey,
                boolean requiresReview,
                Integer expectedFromLine,
                int expectedLineCount,
                boolean expectedWholeFile) {
            Ingestion target = ingestionWith(unparsedFromLine, unparsedLineCount);
            // ファイルの誤りの受信記録は範囲を持たず照会の先頭に来る。ほかに行の誤りの隔離を 1 つ置く（ファイルの誤りでないときの隔離）
            List<ReceivedRecord> quarantined =
                    fileKey ? List.of(fileDefect(target)) : List.of(ownPayload(target, 3, 3));
            given(receivedRecords.countByIngestionAndClassification(target.id(), Classification.QUARANTINED))
                    .willReturn(quarantined.size());
            given(receivedRecords.quarantinedOf(target.id(), IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT))
                    .willReturn(quarantined);

            IngestionSummaryView summary = service.summaryOf(target.id()).orElseThrow();

            assertThat(summary.requiresReview()).as(label).isEqualTo(requiresReview);
            if (requiresReview) {
                assertThat(summary.unreadable())
                        .isEqualTo(new IngestionSummaryView.UnreadableRegion(
                                expectedFromLine, expectedLineCount, expectedWholeFile));
            } else {
                assertThat(summary.unreadable()).isNull();
            }
        }

        @Test
        void 行7_隔離が0件なら要確認でなく隔離の照会を呼ばない() {
            given(receivedRecords.countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                    .willReturn(0);

            IngestionSummaryView summary = service.summaryOf(ingestion.id()).orElseThrow();

            assertThat(summary.requiresReview()).isFalse();
            assertThat(summary.unreadable()).isNull();
            then(receivedRecords).should(never()).quarantinedOf(any(), anyInt());
        }
    }
}
