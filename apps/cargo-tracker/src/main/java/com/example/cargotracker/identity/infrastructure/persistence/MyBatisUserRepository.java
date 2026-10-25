package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.DuplicateEmailException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 利用者のリポジトリの MyBatis 実装。役割は `user_role` に 1 行ずつ置く。
 */
@Repository
public class MyBatisUserRepository implements UserRepository {

    private final IdentityMapper mapper;
    private final Clock clock;

    public MyBatisUserRepository(IdentityMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void add(User user) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        insertUser(user, now);
        user.roles().stream()
                .sorted()
                .forEach(role -> mapper.insertUserRole(user.id().value(), role.name(), now));
    }

    @Override
    public void addRegistered(User user, UserId grantedBy, UtcInstant at) {
        OffsetDateTime now = at.instant().atOffset(ZoneOffset.UTC);
        insertUser(user, now);
        user.roles().stream()
                .sorted()
                .forEach(role -> mapper.insertGrantedUserRole(user.id().value(), role.name(), grantedBy.value(), now));
    }

    private void insertUser(User user, OffsetDateTime now) {
        try {
            mapper.insertUser(
                    new UserRow(
                            user.id().value(),
                            user.companyId().value(),
                            user.email().value(),
                            user.displayName(),
                            user.passwordHash(),
                            user.status().name(),
                            user.version()),
                    now);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEmailException("同じメールアドレスの利用者が既にある", e);
        }
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return mapper.selectUserByEmail(email.value()).map(this::toAggregate);
    }

    @Override
    public boolean changePassword(User user) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        return mapper.updateUserPassword(user.id().value(), user.passwordHash(), now) == 1;
    }

    @Override
    public boolean changeRoles(User changed, long expectedVersion, UserId grantedBy, UtcInstant at) {
        OffsetDateTime now = at.instant().atOffset(ZoneOffset.UTC);
        if (mapper.updateUserVersion(changed.id().value(), expectedVersion, now) != 1) {
            return false;
        }
        Set<String> current = Set.copyOf(mapper.selectUserRoles(changed.id().value()));
        Set<String> next = changed.roles().stream().map(Role::name).collect(Collectors.toSet());
        current.stream()
                .filter(role -> !next.contains(role))
                .sorted()
                .forEach(role -> mapper.deleteUserRole(changed.id().value(), role));
        next.stream()
                .filter(role -> !current.contains(role))
                .sorted()
                .forEach(role -> mapper.insertGrantedUserRole(changed.id().value(), role, grantedBy.value(), now));
        return true;
    }

    @Override
    public boolean changeStatus(User changed, long expectedVersion, UtcInstant at) {
        OffsetDateTime now = at.instant().atOffset(ZoneOffset.UTC);
        return mapper.updateUserStatus(changed.id().value(), changed.status().name(), expectedVersion, now) == 1;
    }

    private static List<UserSummary> summaries(List<UserAdministrationRow> found) {
        return group(found).stream()
                .map(rows -> {
                    UserAdministrationRow first = rows.getFirst();
                    return new UserSummary(
                            new UserId(first.id()),
                            first.companyName(),
                            first.displayName(),
                            new EmailAddress(first.email()),
                            UserStatus.valueOf(first.status()),
                            roles(rows));
                })
                .toList();
    }

    @Override
    public List<UserSummary> findSummariesIn(Set<CompanyId> companies) {
        if (companies.isEmpty()) {
            return List.of();
        }
        return summaries(mapper.selectUserAdministrationRowsIn(
                companies.stream().map(CompanyId::value).toList()));
    }

    @Override
    public Optional<UserDetail> findDetail(UserId id) {
        List<UserAdministrationRow> rows = mapper.selectUserAdministrationRowsById(id.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserAdministrationRow first = rows.getFirst();
        return Optional.of(new UserDetail(
                new UserId(first.id()),
                new CompanyId(first.companyId()),
                first.companyName(),
                CompanyKind.valueOf(first.companyKind()),
                first.displayName(),
                new EmailAddress(first.email()),
                UserStatus.valueOf(first.status()),
                roles(rows),
                first.version()));
    }

    /** SQL の並びを保ったまま、利用者ごとの行にまとめる。 */
    private static List<List<UserAdministrationRow>> group(List<UserAdministrationRow> rows) {
        Map<UUID, List<UserAdministrationRow>> byUser = new LinkedHashMap<>();
        rows.forEach(
                row -> byUser.computeIfAbsent(row.id(), id -> new ArrayList<>()).add(row));
        return List.copyOf(byUser.values());
    }

    private static Set<Role> roles(List<UserAdministrationRow> rows) {
        return rows.stream()
                .map(UserAdministrationRow::role)
                .filter(Objects::nonNull)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<User> findById(UserId id) {
        return mapper.selectUserById(id.value()).map(this::toAggregate);
    }

    private User toAggregate(UserRow row) {
        return User.of(
                new UserId(row.id()),
                new CompanyId(row.companyId()),
                new EmailAddress(row.email()),
                row.displayName(),
                row.passwordHash(),
                UserStatus.valueOf(row.status()),
                mapper.selectUserRoles(row.id()).stream().map(Role::valueOf).collect(Collectors.toSet()),
                row.version());
    }
}
