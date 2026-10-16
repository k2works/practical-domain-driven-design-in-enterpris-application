package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 企業・利用者・役割・監査記録の MyBatis マッパー。SQL は同じパッケージの IdentityMapper.xml に置く。
 */
@Mapper
public interface IdentityMapper {

    void insertCompany(@Param("row") CompanyRow row, @Param("now") OffsetDateTime now);

    Optional<CompanyRow> selectCompany(UUID id);

    void insertUser(@Param("row") UserRow row, @Param("now") OffsetDateTime now);

    void insertUserRole(@Param("userId") UUID userId, @Param("role") String role, @Param("now") OffsetDateTime now);

    Optional<UserRow> selectUserByEmail(String email);

    Optional<UserRow> selectUserById(UUID id);

    int updateUserPassword(
            @Param("id") UUID id, @Param("passwordHash") String passwordHash, @Param("now") OffsetDateTime now);

    List<String> selectUserRoles(UUID userId);

    /** 版を条件にして版を上げる（Bolt 34。役割の変更）。0 件ならほかの更新が先に入った。 */
    int updateUserVersion(
            @Param("id") UUID id, @Param("expectedVersion") long expectedVersion, @Param("now") OffsetDateTime now);

    /** 付与した操作者付きで役割を足す（Bolt 34）。 */
    void insertGrantedUserRole(
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("grantedBy") UUID grantedBy,
            @Param("now") OffsetDateTime now);

    /** 役割を外す（Bolt 34）。 */
    int deleteUserRole(@Param("userId") UUID userId, @Param("role") String role);

    /** 利用者・所属企業・役割を 1 つの SQL で読む（Bolt 32c）。役割の数だけ行が出る。 */
    List<UserStandingRow> selectUserStanding(UUID id);

    void insertAuditRecord(AuditRecordRow row);

    List<AuditRecordRow> selectAuditRecordsByActor(UUID actorUserId);
}
