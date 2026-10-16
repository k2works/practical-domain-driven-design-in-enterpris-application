package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 利用者の管理の照会（Bolt 34b。管理範囲で絞る。US-16 AC2、IA-INV-14）。 */
class UserAdministrationQueryServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final UserAdministrationQueryService service = new UserAdministrationQueryService(users, assignments);

    private final CompanyId inside = new CompanyId(UUID.randomUUID());
    private final CompanyId outside = new CompanyId(UUID.randomUUID());
    private final AuthenticatedActor admin =
            new AuthenticatedActor(new UserId(UUID.randomUUID()), inside, Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");

    private UserDetail detailIn(CompanyId company) {
        return new UserDetail(
                new UserId(UUID.randomUUID()),
                company,
                "A 社",
                CompanyKind.OPERATOR,
                "営業 一郎",
                EmailAddress.of("sales@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES),
                0L);
    }

    @Test
    void 一覧は管理範囲の企業で絞る() {
        UserSummary summary = new UserSummary(
                new UserId(UUID.randomUUID()),
                "A 社",
                "営業 一郎",
                EmailAddress.of("sales@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(users.findSummariesIn(Set.of(inside))).thenReturn(List.of(summary));

        assertThat(service.list(admin)).containsExactly(summary);
    }

    @Test
    void システム管理者でなければ一覧は空で読まない() {
        AuthenticatedActor sales =
                new AuthenticatedActor(new UserId(UUID.randomUUID()), inside, Set.of(Role.SALES), "営業", "A 社");

        assertThat(service.list(sales)).isEmpty();
        verify(users, never()).findSummariesIn(any());
    }

    @Test
    void 管理範囲の利用者の詳細は見つかる() {
        UserDetail detail = detailIn(inside);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.Found(detail));
    }

    @Test
    void 管理範囲外の利用者の詳細は範囲外() {
        UserDetail detail = detailIn(outside);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.OutOfScope());
    }

    @Test
    void いない利用者は見つからない() {
        UserId missing = new UserId(UUID.randomUUID());
        when(users.findDetail(missing)).thenReturn(Optional.empty());

        assertThat(service.detail(admin, missing)).isEqualTo(new UserLookup.NotFound());
    }

    @Test
    void システム管理者でなければ詳細は権限なしで読まない() {
        AuthenticatedActor sales =
                new AuthenticatedActor(new UserId(UUID.randomUUID()), inside, Set.of(Role.SALES), "営業", "A 社");
        UserId target = new UserId(UUID.randomUUID());

        assertThat(service.detail(sales, target)).isEqualTo(new UserLookup.NotPermitted());
        verify(users, never()).findDetail(any());
    }
}
