package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 情報源のリポジトリの MyBatis 実装（Bolt 39b）。読み取りだけを持つ。 */
@Repository
public class MyBatisDataSourceRepository implements DataSourceRepository {

    private final DataSourceMapper mapper;

    public MyBatisDataSourceRepository(DataSourceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DataSource> findById(UUID id) {
        return mapper.selectById(id).map(MyBatisDataSourceRepository::toDomain);
    }

    @Override
    public List<DataSource> findApproved() {
        return mapper.selectApproved().stream()
                .map(MyBatisDataSourceRepository::toDomain)
                .toList();
    }

    @Override
    public int countApproved() {
        return mapper.countApproved();
    }

    private static DataSource toDomain(DataSourceRow row) {
        return new DataSource(
                row.id(), row.provider(), row.recordType(), DataSource.ApprovalStatus.valueOf(row.approvalStatus()));
    }
}
