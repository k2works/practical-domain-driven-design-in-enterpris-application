package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込の要約の照会。S-16 の結果の画面に、取込 ID から情報源の提供元と原本種別、取り込んだ日時、受け取った件数、分類別の件数（採用・重複・隔離）を
 * DB から読み直して返す（Bolt 39c）。隔離があれば、隔離の注記をファイルの行の順（並びは受信記録の照会が決める）に最初の
 * {@link #SHOWN_QUARANTINE_LIMIT} 件だけ読み、示さなかった隔離の数を隔離の件数から引いて返す。隔離が 0 件なら注記を読まない（Bolt 39c3）。取込全体の要確認と取り込めなかった部分は、取込の取り込めなかった物理行の数と、読んだ隔離の
 * 受信記録の中のファイル全体の原本を指すもの（ファイルの誤り）から復元する（要確認は列で持たない。ポートに読み出しを足さない。Bolt 39c4）。
 * 取込のサービスの結果（取込の結果）とは別の値で、画面を読み込み直しても取り込み直さない（PRG）。
 * 組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class IngestionSummaryQueryService {

    /** 結果の画面に示す隔離の注記の上限。すべての隔離は S-17 の一覧で見る（39d。Bolt 39c3 の開始準備の決定 (4)）。 */
    public static final int SHOWN_QUARANTINE_LIMIT = 100;

    private final IngestionRepository ingestions;
    private final DataSourceRepository dataSources;
    private final ReceivedRecordRepository receivedRecords;

    public IngestionSummaryQueryService(
            IngestionRepository ingestions,
            DataSourceRepository dataSources,
            ReceivedRecordRepository receivedRecords) {
        this.ingestions = ingestions;
        this.dataSources = dataSources;
        this.receivedRecords = receivedRecords;
    }

    /** 取込の要約。ない取込 ID は空。 */
    @Transactional(readOnly = true)
    public Optional<IngestionSummaryView> summaryOf(UUID ingestionId) {
        return ingestions.findById(ingestionId).map(this::summary);
    }

    private IngestionSummaryView summary(Ingestion ingestion) {
        // 取込は情報源への外部キーを持つので、情報源は必ずある
        DataSource source = dataSources.findById(ingestion.dataSourceId()).orElseThrow();
        int quarantinedCount = countOf(ingestion, Classification.QUARANTINED);
        List<ReceivedRecord> quarantined = quarantinedCount == 0
                ? List.of()
                : receivedRecords.quarantinedOf(ingestion.id(), SHOWN_QUARANTINE_LIMIT);
        List<String> notes =
                quarantined.stream().map(ReceivedRecord::classificationNote).toList();
        return new IngestionSummaryView(
                ingestion.id(),
                source.provider(),
                source.recordType(),
                ingestion.ingestedAt(),
                ingestion.receivedCount(),
                countOf(ingestion, Classification.ADOPTED),
                countOf(ingestion, Classification.DUPLICATE),
                quarantinedCount,
                notes,
                quarantinedCount - notes.size(),
                unreadableOf(ingestion, quarantined));
    }

    /**
     * 取り込めなかった部分（要確認でなければ {@code null}。Bolt 39c4 の開始準備の決定 (2)・(3)）。取込の取り込めなかった物理行の数が 0 より
     * 大きいか、読んだ隔離の受信記録にファイル全体の原本を指すもの（ファイルの誤り）があれば要確認。ファイルの誤りの受信記録はその取込の
     * 1 件だけで範囲を持たず照会の先頭に来るので、読んだ隔離の中にある。始まりと数は取込の列のまま。
     */
    private static IngestionSummaryView.UnreadableRegion unreadableOf(
            Ingestion ingestion, List<ReceivedRecord> quarantined) {
        boolean wholeFile = quarantined.stream()
                .anyMatch(received -> received.payloadObjectKey().equals(ingestion.fileObjectKey()));
        if (!wholeFile && ingestion.unparsedLineCount() == 0) {
            return null;
        }
        return new IngestionSummaryView.UnreadableRegion(
                ingestion.unparsedFromLine(), ingestion.unparsedLineCount(), wholeFile);
    }

    private int countOf(Ingestion ingestion, Classification classification) {
        return receivedRecords.countByIngestionAndClassification(ingestion.id(), classification);
    }
}
