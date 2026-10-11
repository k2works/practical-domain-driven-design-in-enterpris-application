package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TOTP の資格・回復コード・ログインの失敗の MyBatis マッパー（Bolt 30）。SQL は同じパッケージの
 * AuthenticationFactorMapper.xml に置く。更新は条件付きの 1 文で、更新した件数を返す。
 */
@Mapper
public interface AuthenticationFactorMapper {

    void insertTotpCredential(TotpCredentialRow row);

    Optional<TotpCredentialRow> selectTotpCredential(UUID userId);

    int updateLastUsedStepIfLater(@Param("userId") UUID userId, @Param("step") long step);

    void deleteRecoveryCodes(UUID userId);

    void insertRecoveryCode(RecoveryCodeRow row);

    List<RecoveryCodeRow> selectUnusedRecoveryCodes(UUID userId);

    int updateRecoveryCodeUsedIfUnused(@Param("id") UUID id, @Param("usedAt") OffsetDateTime usedAt);

    Optional<LoginFailureRow> selectLoginFailure(UUID userId);

    void insertLoginFailureIfAbsent(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    int incrementLoginFailureIfNotLocked(
            @Param("userId") UUID userId,
            @Param("failedAt") OffsetDateTime failedAt,
            @Param("maxFailures") int maxFailures,
            @Param("lockedUntil") OffsetDateTime lockedUntil);

    void resetLoginFailure(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
