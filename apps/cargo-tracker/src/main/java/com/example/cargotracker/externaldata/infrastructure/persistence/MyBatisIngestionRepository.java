package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.UserId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 取込のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加と ID で引くこと（Bolt 39c）だけを持つ。 */
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
                ingestion.receivedCount(),
                ingestion.unparsedFromLine(),
                ingestion.unparsedLineCount()));
    }

    @Override
    public Optional<Ingestion> findById(UUID id) {
        return mapper.selectById(id).map(MyBatisIngestionRepository::toDomain);
    }

    private static Ingestion toDomain(IngestionRow row) {
        return new Ingestion(
                row.id(),
                row.dataSourceId(),
                new PayloadObjectKey(row.fileObjectKey()),
                new PayloadHash(row.fileHash()),
                new UserId(row.ingestedBy()),
                row.ingestedAt().toInstant(),
                row.receivedCount(),
                row.unparsedFromLine(),
                row.unparsedLineCount());
    }
}
