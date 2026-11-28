package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 開始判定の照会。承認済みの情報源の件数と、パイロットを開始できるか、開始できない理由を返す（ED-INV-01、BR-18。Bolt 39b）。
 * 組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class PilotReadinessQueryService {

    private final DataSourceRepository dataSources;

    public PilotReadinessQueryService(DataSourceRepository dataSources) {
        this.dataSources = dataSources;
    }

    /** パイロットの開始判定。 */
    @Transactional(readOnly = true)
    public PilotReadiness readiness() {
        return new PilotReadiness(dataSources.countApproved());
    }
}
