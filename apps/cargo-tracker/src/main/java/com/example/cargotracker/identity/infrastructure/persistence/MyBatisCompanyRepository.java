package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.domain.CompanyId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        return mapper.selectCompany(id.value()).map(MyBatisCompanyRepository::toCompany);
    }

    @Override
    public List<Company> findAllIn(Set<CompanyId> ids) {
        if (ids.isEmpty()) {
            // 空の IN 句は SQL の誤りになるので、問い合わせない
            return List.of();
        }
        return mapper.selectCompaniesIn(ids.stream().map(CompanyId::value).toList()).stream()
                .map(MyBatisCompanyRepository::toCompany)
                .toList();
    }

    private static Company toCompany(CompanyRow row) {
        return Company.of(new CompanyId(row.id()), row.name(), CompanyKind.valueOf(row.kind()), row.active());
    }
}
