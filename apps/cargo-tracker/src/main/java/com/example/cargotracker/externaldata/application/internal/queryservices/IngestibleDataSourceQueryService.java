package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取り込める情報源の照会。S-16 の選ぶ画面に、承認済みの情報源を提供元・原本種別の順に並べ、情報源 ID から原本種別を引く（Bolt 39c）。
 * 原本種別は承認の状態を問わずに引く（承認は取込のサービスが確かめる。BR-18）。原本種別を取込ファイルの種別に変えるのは画面の層で、この照会は
 * {@code interfaces.file} に依存しない。組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class IngestibleDataSourceQueryService {

    private final DataSourceRepository dataSources;

    public IngestibleDataSourceQueryService(DataSourceRepository dataSources) {
        this.dataSources = dataSources;
    }

    /** 取り込める情報源（承認済み。提供元・原本種別の Java の {@link String} の順。ポートの返す順にも DB の照合順序にも頼らない。Bolt 39d4 の開始準備の決定 (9)）。 */
    @Transactional(readOnly = true)
    public List<IngestibleDataSource> ingestibleSources() {
        return dataSources.findApproved().stream()
                .sorted(Comparator.comparing(DataSource::provider).thenComparing(DataSource::recordType))
                .map(source -> new IngestibleDataSource(source.id(), source.provider(), source.recordType()))
                .toList();
    }

    /** 情報源の原本種別（{@code data_source.record_type}）。承認の状態を問わない。情報源がなければ空。 */
    @Transactional(readOnly = true)
    public Optional<String> recordTypeOf(UUID dataSourceId) {
        return dataSources.findById(dataSourceId).map(DataSource::recordType);
    }
}
