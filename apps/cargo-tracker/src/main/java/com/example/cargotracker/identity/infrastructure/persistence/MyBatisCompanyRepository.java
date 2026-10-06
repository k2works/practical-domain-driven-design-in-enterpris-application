package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 企業のリポジトリの MyBatis 実装。
 */
@Repository
public class MyBatisCompanyRepository implements CompanyRepository {

    @Override
    public void add(Company company) {
        // Red の骨組み
    }

    @Override
    public Optional<Company> findById(CompanyId id) {
        return Optional.empty();
    }
}
