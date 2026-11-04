package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込の要約の照会。S-16 の結果の画面に、取込 ID から情報源の提供元と原本種別、取り込んだ日時、受け取った件数、分類別の件数（採用・重複・隔離）を
 * DB から読み直して返す（Bolt 39c）。取込のサービスの結果（取込の結果）とは別の値で、画面を読み込み直しても取り込み直さない（PRG）。
 * 組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class IngestionSummaryQueryService {

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
        return new IngestionSummaryView(
                ingestion.id(),
                source.provider(),
                source.recordType(),
                ingestion.ingestedAt(),
                ingestion.receivedCount(),
                countOf(ingestion, Classification.ADOPTED),
                countOf(ingestion, Classification.DUPLICATE),
                countOf(ingestion, Classification.QUARANTINED));
    }

    private int countOf(Ingestion ingestion, Classification classification) {
        return receivedRecords.countByIngestionAndClassification(ingestion.id(), classification);
    }
}
