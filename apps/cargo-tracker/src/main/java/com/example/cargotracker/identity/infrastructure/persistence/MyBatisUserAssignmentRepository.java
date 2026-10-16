package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 担当範囲のリポジトリの MyBatis 実装（Bolt 34b）。`COMPANY` の行だけを読む（`BOOKING` は W6）。
 */
@Repository
public class MyBatisUserAssignmentRepository implements UserAssignmentRepository {

    private final IdentityMapper mapper;

    public MyBatisUserAssignmentRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdministrationScope scopeOf(UserId admin) {
        return new AdministrationScope(mapper.selectManagedCompanies(admin.value()).stream()
                .map(CompanyId::new)
                .collect(Collectors.toUnmodifiableSet()));
    }
}
