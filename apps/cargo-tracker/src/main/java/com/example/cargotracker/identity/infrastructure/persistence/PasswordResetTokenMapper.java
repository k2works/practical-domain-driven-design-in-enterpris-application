package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * password の再設定のトークンの MyBatis マッパー（Bolt 33）。SQL は同じパッケージの PasswordResetTokenMapper.xml に置く。
 */
@Mapper
public interface PasswordResetTokenMapper {

    Optional<UUID> lockUser(UUID userId);

    int countByRequestId(UUID requestId);

    Optional<OffsetDateTime> selectLastIssuedAt(@Param("userId") UUID userId, @Param("purpose") String purpose);

    int invalidateUnused(@Param("userId") UUID userId, @Param("at") OffsetDateTime at);

    void insertToken(PasswordResetTokenRow row);

    Optional<PasswordResetTokenRow> selectByTokenHash(String tokenHash);

    Optional<PasswordResetTokenRow> selectById(UUID id);

    int markUsedIfUsable(@Param("id") UUID id, @Param("at") OffsetDateTime at);
}
