package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;

/** 取込のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加だけを持つ。 */
@Repository
public class MyBatisIngestionRepository implements IngestionRepository {

    private final IngestionMapper mapper;

    public MyBatisIngestionRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(Ingestion ingestion) {
        mapper.insert(new IngestionRow(
                ingestion.id(),
                ingestion.dataSourceId(),
                ingestion.fileObjectKey().value(),
                ingestion.fileHash().value(),
                ingestion.ingestedBy().value(),
                ingestion.ingestedAt().atOffset(ZoneOffset.UTC),
                ingestion.receivedCount()));
    }
}
