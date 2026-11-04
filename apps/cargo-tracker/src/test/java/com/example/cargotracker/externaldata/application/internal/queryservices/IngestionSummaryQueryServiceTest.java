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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 取込の要約の照会（Bolt 39c3 の行 16・17。開始準備の決定 (4)、T-236・T-244）。ポートを mock にし、隔離の注記を最初の
 * {@link IngestionSummaryQueryService#SHOWN_QUARANTINE_LIMIT} 件だけ読み、残りの件数を隔離の件数から引くこと、隔離が 0 件なら照会を
 * 呼ばないこと（呼び出しの記録）を確かめる。
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
                new PhysicalLineRange(n + 1, n + 1));
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
}
