package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.DuplicateEmailException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
        try {
            mapper.insertUser(
                    new UserRow(
                            user.id().value(),
                            user.companyId().value(),
                            user.email().value(),
                            user.displayName(),
                            user.passwordHash(),
                            user.status().name()),
                    now);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEmailException("同じメールアドレスの利用者が既にある", e);
        }
        user.roles().stream()
                .sorted()
                .forEach(role -> mapper.insertUserRole(user.id().value(), role.name(), now));
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return mapper.selectUserByEmail(email.value()).map(this::toAggregate);
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
                mapper.selectUserRoles(row.id()).stream().map(Role::valueOf).collect(Collectors.toSet()));
    }
}
