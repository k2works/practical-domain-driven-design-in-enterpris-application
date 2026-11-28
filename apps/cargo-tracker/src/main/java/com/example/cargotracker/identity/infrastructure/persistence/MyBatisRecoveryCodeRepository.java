package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 回復コードのリポジトリの MyBatis 実装（Bolt 30）。
 */
@Repository
public class MyBatisRecoveryCodeRepository implements RecoveryCodeRepository {

    private final AuthenticationFactorMapper mapper;

    public MyBatisRecoveryCodeRepository(AuthenticationFactorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void replaceAll(UserId userId, List<RecoveryCode> codes) {
        List<RecoveryCode> issued = RecoveryCode.requireIssuedSet(userId, codes);
        mapper.deleteRecoveryCodes(userId.value());
        issued.forEach(code -> mapper.insertRecoveryCode(
                new RecoveryCodeRow(code.id(), userId.value(), code.codeHash(), null, toOffset(code.issuedAt()))));
    }

    @Override
    public List<RecoveryCode> findUnusedByUserId(UserId userId) {
        return mapper.selectUnusedRecoveryCodes(userId.value()).stream()
                .map(row -> RecoveryCode.reconstitute(
                        row.id(),
                        new UserId(row.userId()),
                        row.codeHash(),
                        row.usedAt() == null
                                ? null
                                : new UtcInstant(row.usedAt().toInstant()),
                        new UtcInstant(row.issuedAt().toInstant())))
                .toList();
    }

    @Override
    public boolean use(UserId userId, UUID recoveryCodeId, UtcInstant usedAt) {
        return mapper.updateRecoveryCodeUsedIfUnused(userId.value(), recoveryCodeId, toOffset(usedAt)) == 1;
    }

    @Override
    public void removeAll(UserId userId) {
        mapper.deleteRecoveryCodes(userId.value());
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}
