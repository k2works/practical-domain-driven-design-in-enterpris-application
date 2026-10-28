package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 担当範囲のリポジトリの MyBatis 実装（Bolt 34b）。`COMPANY` の行だけを読み書きする（担当案件の `BOOKING` の行は作らない。担当案件は
 * 担当企業で近似する。Bolt 38e の決定 (10)）。担当企業を読む SQL は、担当範囲のある役割（{@link RoleAssignmentPolicy}）を条件に渡す。
 */
@Repository
public class MyBatisUserAssignmentRepository implements UserAssignmentRepository {

    private final IdentityMapper mapper;

    public MyBatisUserAssignmentRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdministrationScope scopeOf(UserId admin) {
        return new AdministrationScope(companiesOf(mapper.selectManagedCompanies(admin.value())));
    }

    @Override
    public AssignedCompanies assignedCompaniesOf(UserId user) {
        List<String> roles = RoleAssignmentPolicy.assignmentScopedRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();
        return new AssignedCompanies(companiesOf(mapper.selectAssignedCompanies(user.value(), roles)));
    }

    @Override
    public boolean replaceCompanies(
            UserId user,
            long expectedVersion,
            AdministrationScope scope,
            AssignedCompanies selected,
            UserId grantedBy,
            UtcInstant at) {
        OffsetDateTime now = at.instant().atOffset(ZoneOffset.UTC);
        // 版を条件にした更新を先に流す（行がロックされ、同時の変更は後の文が 0 件になる）
        if (mapper.updateUserVersion(user.value(), expectedVersion, now) != 1) {
            return false;
        }
        // 呼ぶ側が担当範囲のある役割を確かめているので、役割で絞らずに今の COMPANY の行を読む
        Set<CompanyId> current = companiesOf(mapper.selectCompanyRows(user.value()));
        Set<CompanyId> removed = new HashSet<>(current);
        removed.removeIf(company -> !scope.covers(company) || selected.contains(company));
        Set<CompanyId> added = new HashSet<>(selected.companies());
        added.removeAll(current);
        removed.forEach(company -> mapper.deleteCompanyAssignment(user.value(), company.value()));
        added.forEach(company -> mapper.insertCompanyAssignment(user.value(), company.value(), grantedBy.value(), now));
        return true;
    }

    private static Set<CompanyId> companiesOf(List<UUID> ids) {
        return ids.stream().map(CompanyId::new).collect(Collectors.toUnmodifiableSet());
    }
}
