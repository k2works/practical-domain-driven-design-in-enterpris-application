package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.domain.CompanyId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 企業のリポジトリの MyBatis 実装。
 */
@Repository
public class MyBatisCompanyRepository implements CompanyRepository {

    private final IdentityMapper mapper;
    private final Clock clock;

    public MyBatisCompanyRepository(IdentityMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void add(Company company) {
        mapper.insertCompany(
                new CompanyRow(
                        company.id().value(), company.name(), company.kind().name(), company.active()),
                OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Override
    public Optional<Company> findById(CompanyId id) {
        return mapper.selectCompany(id.value())
                .map(row ->
                        Company.of(new CompanyId(row.id()), row.name(), CompanyKind.valueOf(row.kind()), row.active()));
    }
}
