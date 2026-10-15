package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.DuplicateTotpEnrollmentException;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * TOTP の資格のリポジトリの MyBatis 実装（Bolt 30）。
 */
@Repository
public class MyBatisTotpCredentialRepository implements TotpCredentialRepository {

    private final AuthenticationFactorMapper mapper;

    public MyBatisTotpCredentialRepository(AuthenticationFactorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(TotpCredential credential) {
        try {
            mapper.insertTotpCredential(rowOf(credential));
        } catch (DuplicateKeyException e) {
            throw new DuplicateTotpEnrollmentException("その利用者の TOTP の資格は既にある", e);
        }
    }

    @Override
    public boolean replace(TotpCredential credential, EncryptedSecret expectedSecret) {
        return mapper.updateTotpCredentialIfUnchanged(rowOf(credential), expectedSecret.value()) == 1;
    }

    @Override
    public Optional<TotpCredential> findByUserId(UserId userId) {
        return mapper.selectTotpCredential(userId.value())
                .map(row -> TotpCredential.reconstitute(
                        new UserId(row.userId()),
                        new EncryptedSecret(row.secretEncrypted()),
                        row.lastUsedStep(),
                        new UtcInstant(row.enrolledAt().toInstant())));
    }

    @Override
    public boolean useStep(UserId userId, EncryptedSecret secret, long step) {
        if (step < 0) {
            throw new IllegalArgumentException("時間の刻みは 0 以上");
        }
        return mapper.updateLastUsedStepIfLater(userId.value(), secret.value(), step) == 1;
    }

    private static TotpCredentialRow rowOf(TotpCredential credential) {
        return new TotpCredentialRow(
                credential.userId().value(),
                credential.secret().value(),
                credential.lastUsedStep(),
                credential.enrolledAt().instant().atOffset(ZoneOffset.UTC));
    }
}
