package com.example.cargotracker.externaldata.infrastructure.config;

import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestibleDataSourceQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadinessQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部データコンテキストの組み立て（Bolt 39b）。アプリケーションサービスは部品探索の外なので、ここで {@code @Bean} として組む
 * （CargoTrackerApplication、2026-10-03 の人の決定）。
 */
@Configuration(proxyBeanMethods = false)
public class ExternalDataConfiguration {

    @Bean
    PilotReadinessQueryService pilotReadinessQueryService(DataSourceRepository dataSources) {
        return new PilotReadinessQueryService(dataSources);
    }

    /** S-16 の選ぶ画面の照会（Bolt 39c）。 */
    @Bean
    IngestibleDataSourceQueryService ingestibleDataSourceQueryService(DataSourceRepository dataSources) {
        return new IngestibleDataSourceQueryService(dataSources);
    }

    /** S-16 の結果の画面の照会（Bolt 39c）。 */
    @Bean
    IngestionSummaryQueryService ingestionSummaryQueryService(
            IngestionRepository ingestions,
            DataSourceRepository dataSources,
            ReceivedRecordRepository receivedRecords) {
        return new IngestionSummaryQueryService(ingestions, dataSources, receivedRecords);
    }

    /** S-17 の判断を待つ隔離の一覧と判断の画面の照会（Bolt 39d。この外部版の採用値は Bolt 39d3）。 */
    @Bean
    UndecidedRecordQueryService undecidedRecordQueryService(
            ReceivedRecordRepository receivedRecords,
            IngestionRepository ingestions,
            DataSourceRepository dataSources,
            AdoptedValueRepository adoptedValues) {
        return new UndecidedRecordQueryService(receivedRecords, ingestions, dataSources, adoptedValues);
    }

    /** S-17 の隔離の却下（Bolt 39d2）。 */
    @Bean
    ReceivedRecordDecisionService receivedRecordDecisionService(
            ReceivedRecordRepository receivedRecords,
            ReceivedRecordDecisionRepository decisions,
            IngestionRepository ingestions,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new ReceivedRecordDecisionService(receivedRecords, decisions, ingestions, eventPublisher, clock);
    }

    @Bean
    IngestionService ingestionService(
            DataSourceRepository dataSources,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords,
            AdoptedValueRepository adoptedValues,
            ExternalPayloadStorage payloadStorage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new IngestionService(
                dataSources,
                ingestions,
                externalRecords,
                receivedRecords,
                adoptedValues,
                payloadStorage,
                eventPublisher,
                clock);
    }
}
