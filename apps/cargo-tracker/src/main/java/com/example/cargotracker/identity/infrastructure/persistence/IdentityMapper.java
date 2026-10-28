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

    List<CompanyRow> selectCompaniesIn(@Param("companyIds") List<UUID> companyIds);

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

    /** 版を条件にして利用状態を変え、版を上げる（Bolt 34d。利用停止と再開）。0 件ならほかの更新が先に入った。 */
    int updateUserStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") OffsetDateTime now);

    /** 付与した操作者付きで役割を足す（Bolt 34）。 */
    void insertGrantedUserRole(
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("grantedBy") UUID grantedBy,
            @Param("now") OffsetDateTime now);

    /** S-20 の一覧の行のうち、企業で絞ったもの（Bolt 34b。管理範囲）。利用者ごとに役割の数だけ行が出る。 */
    List<UserAdministrationRow> selectUserAdministrationRowsIn(@Param("companyIds") List<UUID> companyIds);

    /** システム管理者に管理範囲として割り当てた企業 ID（Bolt 34b）。 */
    List<UUID> selectManagedCompanies(UUID adminId);

    /**
     * 利用者の COMPANY の行の企業 ID を、役割で絞らずに読む（Bolt 38e。担当企業の置き換えの前に今の行を読む。管理範囲・担当企業のどちらの
     * 意味も持たせない。開発レビューの低-3）。
     */
    List<UUID> selectCompanyRows(UUID userId);

    /** 担当範囲のある役割を持つ利用者の担当企業 ID（Bolt 38e）。役割がなければ空。 */
    List<UUID> selectAssignedCompanies(@Param("userId") UUID userId, @Param("roles") List<String> roles);

    /** 担当企業を 1 つ外す（Bolt 38e。COMPANY の行だけ）。 */
    int deleteCompanyAssignment(@Param("userId") UUID userId, @Param("companyId") UUID companyId);

    /** 付与した操作者付きで担当企業を 1 つ足す（Bolt 38e）。 */
    void insertCompanyAssignment(
            @Param("userId") UUID userId,
            @Param("companyId") UUID companyId,
            @Param("grantedBy") UUID grantedBy,
            @Param("now") OffsetDateTime now);

    /** S-20 の詳細の行（Bolt 34。Bolt 38c からログインの資格を持つ詳細だけの行）。役割の数だけ行が出る。 */
    List<UserDetailRow> selectUserDetailRowsById(UUID id);

    /** 役割を外す（Bolt 34）。 */
    int deleteUserRole(@Param("userId") UUID userId, @Param("role") String role);

    /** 利用者・所属企業・役割を 1 つの SQL で読む（Bolt 32c）。役割の数だけ行が出る。 */
    List<UserStandingRow> selectUserStanding(UUID id);

    void insertAuditRecord(AuditRecordRow row);

    List<AuditRecordRow> selectAuditRecordsByActor(UUID actorUserId);

    /** 対象の利用者の今の停止の記録を、操作者の今の表示名とともに読む（Bolt 38c2）。対象の ID は文字列（列が VARCHAR）。 */
    SuspensionRecordRow selectLatestUserSuspension(@Param("targetId") String targetId);
}
