package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 再設定のトークンのリポジトリの MyBatis 実装（Bolt 33）。
 */
@Repository
public class MyBatisPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final PasswordResetTokenMapper mapper;

    public MyBatisPasswordResetTokenRepository(PasswordResetTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean lockForIssue(UserId userId) {
        return mapper.lockUser(userId.value()).isPresent();
    }

    @Override
    public boolean existsByRequestId(UUID requestId) {
        return mapper.countByRequestId(requestId) > 0;
    }

    @Override
    public Optional<UtcInstant> findLastIssuedAt(UserId userId) {
        return mapper.selectLastIssuedAt(userId.value()).map(issuedAt -> new UtcInstant(issuedAt.toInstant()));
    }

    @Override
    public int invalidateUnused(UserId userId, UtcInstant at) {
        return mapper.invalidateUnused(userId.value(), toOffset(at));
    }

    @Override
    public void add(PasswordResetToken token) {
        mapper.insertToken(new PasswordResetTokenRow(
                token.id(),
                token.requestId(),
                token.userId().value(),
                token.tokenHash(),
                toOffset(token.issuedAt()),
                toOffset(token.expiresAt()),
                token.usedAt()
                        .map(MyBatisPasswordResetTokenRepository::toOffset)
                        .orElse(null),
                token.invalidatedAt()
                        .map(MyBatisPasswordResetTokenRepository::toOffset)
                        .orElse(null)));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return mapper.selectByTokenHash(tokenHash).map(MyBatisPasswordResetTokenRepository::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findById(UUID id) {
        return mapper.selectById(id).map(MyBatisPasswordResetTokenRepository::toDomain);
    }

    @Override
    public boolean markUsed(UUID id, UtcInstant at) {
        return mapper.markUsedIfUsable(id, toOffset(at)) == 1;
    }

    private static PasswordResetToken toDomain(PasswordResetTokenRow row) {
        return PasswordResetToken.reconstitute(
                row.id(),
                row.requestId(),
                new UserId(row.userId()),
                row.tokenHash(),
                toUtc(row.issuedAt()),
                toUtc(row.expiresAt()),
                row.usedAt() == null ? null : toUtc(row.usedAt()),
                row.invalidatedAt() == null ? null : toUtc(row.invalidatedAt()));
    }

    private static UtcInstant toUtc(OffsetDateTime value) {
        return new UtcInstant(value.toInstant());
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}
