package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 監査記録。操作者・時刻・操作・結果・理由の追記専用の記録（IA-INV-07）。
 * Bolt 14 はログインの成功・失敗とログアウトを、同じ request の中で同期に書く（IA-INV-08）。
 * Bolt 34 で対象と変更前後（{@link AuditTarget}）を足した。承認・出典の項目は使う Bolt で足す。
 */
@AggregateRoot
public final class AuditRecord {

    /** 理由の引数の名前（null の確かめの文）。 */
    private static final String REASON_ARGUMENT = "reason";

    /** 操作者の引数の名前（null の確かめの文）。 */
    private static final String USER_ARGUMENT = "user";

    /** 操作者の企業の引数の名前（null の確かめの文）。 */
    private static final String COMPANY_ARGUMENT = "company";

    /** 対象の種類: 利用者（`audit_record.target_type`）。 */
    private static final String USER_TARGET = "USER";

    private final UUID id;
    private final UtcInstant occurredAt;
    private final UserId actorUserId;
    private final CompanyId actorCompanyId;
    private final AuditAction action;
    private final AuditResult result;
    private final String reason;
    private final AuditTarget target;

    private AuditRecord(
            UUID id,
            UtcInstant occurredAt,
            UserId actorUserId,
            CompanyId actorCompanyId,
            AuditAction action,
            AuditResult result,
            String reason,
            AuditTarget target) {
        this.id = Objects.requireNonNull(id, "id");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorUserId = actorUserId;
        this.actorCompanyId = actorCompanyId;
        this.action = Objects.requireNonNull(action, "action");
        this.result = Objects.requireNonNull(result, "result");
        this.reason = reason;
        this.target = target;
    }

    private AuditRecord(
            UUID id,
            UtcInstant occurredAt,
            UserId actorUserId,
            CompanyId actorCompanyId,
            AuditAction action,
            AuditResult result,
            String reason) {
        this(id, occurredAt, actorUserId, actorCompanyId, action, result, reason, null);
    }

    /** password の段の成功を記録する（2 つの要素がそろったログインの成功とは分ける。Bolt 31、ADR-012 の見直しの 1）。 */
    public static AuditRecord passwordVerified(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.PASSWORD_VERIFIED, AuditResult.SUCCESS, null);
    }

    /** TOTP の登録を記録する（Bolt 31b、US-18 AC7。先取りの登録を後から追えるようにする。ADR-011）。 */
    public static AuditRecord totpEnrolled(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.TOTP_ENROLLED, AuditResult.SUCCESS, null);
    }

    /** 認証アプリの再登録を記録する（Bolt 33b、US-18 AC9。成功だけ）。 */
    public static AuditRecord totpReEnrolled(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.TOTP_RE_ENROLLED, AuditResult.SUCCESS, null);
    }

    /** 回復コードの使用を記録する（Bolt 31c、US-18 AC9 のログインの分。どのコードを使ったかは残さない）。 */
    public static AuditRecord recoveryCodeUsed(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.RECOVERY_CODE_USED, AuditResult.SUCCESS, null);
    }

    /**
     * ロックを記録する（Bolt 32、US-18 AC3、OBS-09）。5 回目の失敗でロックしたときに、その失敗の記録と同じトランザクションで書く。
     */
    public static AuditRecord accountLocked(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.ACCOUNT_LOCKED, AuditResult.SUCCESS, null);
    }

    /**
     * session の取消しを記録する（Bolt 32c、US-18 AC6、IA-INV-04）。request ごとの確かめで利用停止・無効な企業・利用者なしを
     * 見つけて session を捨てたとき。結果は失敗（その session を通さなかった）。操作者は session の主体で、利用者が消えていても
     * 追跡できるように残す。
     */
    public static AuditRecord sessionRevoked(
            UserId user, CompanyId company, AuthenticationRejection reason, UtcInstant at) {
        Objects.requireNonNull(user, USER_ARGUMENT);
        Objects.requireNonNull(company, COMPANY_ARGUMENT);
        Objects.requireNonNull(reason, REASON_ARGUMENT);
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.SESSION_REVOKED, AuditResult.FAILURE, reason.name());
    }

    /** ログインの成功を記録する。 */
    public static AuditRecord loginSucceeded(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS, null);
    }

    /**
     * ログインの失敗を記録する。利用者が分からない（存在しないメールアドレス）ときは、利用者と企業を null にする。
     */
    public static AuditRecord loginFailed(
            UserId user, CompanyId company, AuthenticationRejection reason, UtcInstant at) {
        Objects.requireNonNull(reason, REASON_ARGUMENT);
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.LOGIN_FAILED, AuditResult.FAILURE, reason.name());
    }

    /**
     * password の再設定の依頼を受け付けたことを記録する（Bolt 33、US-18 AC8、ADR-012）。有効な利用者の依頼で、結果は成功（理由なし）。
     * 画面の応答は、受け付けなかった依頼（{@link #passwordResetRejected}）と同じ。
     */
    public static AuditRecord passwordResetRequested(UserId user, CompanyId company, UtcInstant at) {
        Objects.requireNonNull(user, USER_ARGUMENT);
        Objects.requireNonNull(company, COMPANY_ARGUMENT);
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.PASSWORD_RESET_REQUESTED, AuditResult.SUCCESS, null);
    }

    /**
     * password の再設定の依頼を受け付けなかったことを記録する（Bolt 33）。登録のないメールアドレスは利用者と企業を null にし、メールアドレスを
     * 残さない。利用停止・企業の無効は理由を残す。結果は失敗（トークンを発行しない）。
     */
    public static AuditRecord passwordResetRejected(
            UserId user, CompanyId company, AuthenticationRejection reason, UtcInstant at) {
        Objects.requireNonNull(reason, REASON_ARGUMENT);
        return new AuditRecord(
                UUID.randomUUID(),
                at,
                user,
                company,
                AuditAction.PASSWORD_RESET_REQUESTED,
                AuditResult.FAILURE,
                reason.name());
    }

    /**
     * password の再設定を終えたことを記録する（Bolt 33a、US-18 AC8）。A-06 で新しい password を設定し、トークンを使ったとき。結果は成功。
     */
    public static AuditRecord passwordResetCompleted(UserId user, CompanyId company, UtcInstant at) {
        Objects.requireNonNull(user, USER_ARGUMENT);
        Objects.requireNonNull(company, COMPANY_ARGUMENT);
        return new AuditRecord(
                UUID.randomUUID(), at, user, company, AuditAction.PASSWORD_RESET_COMPLETED, AuditResult.SUCCESS, null);
    }

    /**
     * システム管理者が利用者の役割を変えたことを記録する（Bolt 34、US-16 AC1 の役割の分）。対象は利用者と更新した後の版、変更前後は
     * 役割の名前を {@link Role} の宣言の順にカンマでつないだ文字列。結果は成功。
     */
    public static AuditRecord rolesChanged(
            UserId actor,
            CompanyId actorCompany,
            UserId targetUser,
            long targetVersion,
            Set<Role> before,
            Set<Role> after,
            UtcInstant at) {
        Objects.requireNonNull(actor, USER_ARGUMENT);
        Objects.requireNonNull(actorCompany, COMPANY_ARGUMENT);
        Objects.requireNonNull(targetUser, "targetUser");
        return new AuditRecord(
                UUID.randomUUID(),
                at,
                actor,
                actorCompany,
                AuditAction.ROLES_CHANGED,
                AuditResult.SUCCESS,
                null,
                new AuditTarget(
                        USER_TARGET,
                        targetUser.value().toString(),
                        Long.toString(targetVersion),
                        roleNames(before),
                        roleNames(after)));
    }

    private static String roleNames(Set<Role> roles) {
        if (roles.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(roles).stream().map(Role::name).collect(Collectors.joining(","));
    }

    /** ログアウトを記録する。 */
    public static AuditRecord logout(UserId user, CompanyId company, UtcInstant at) {
        return new AuditRecord(UUID.randomUUID(), at, user, company, AuditAction.LOGOUT, AuditResult.SUCCESS, null);
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    public static AuditRecord reconstitute(
            UUID id,
            UtcInstant occurredAt,
            UserId actorUserId,
            CompanyId actorCompanyId,
            AuditAction action,
            AuditResult result,
            String reason,
            AuditTarget target) {
        return new AuditRecord(id, occurredAt, actorUserId, actorCompanyId, action, result, reason, target);
    }

    public UUID id() {
        return id;
    }

    public UtcInstant occurredAt() {
        return occurredAt;
    }

    /** 操作者。存在しないメールアドレスでのログインの失敗では null。 */
    public UserId actorUserId() {
        return actorUserId;
    }

    /** 操作者の企業。存在しないメールアドレスでのログインの失敗では null。 */
    public CompanyId actorCompanyId() {
        return actorCompanyId;
    }

    public AuditAction action() {
        return action;
    }

    public AuditResult result() {
        return result;
    }

    /** 理由（ログインの失敗の理由など）。ないときは null。 */
    public String reason() {
        return reason;
    }

    /** 対象と変更前後（役割の変更など。Bolt 34）。対象のない記録（ログインなど）では null。 */
    public AuditTarget target() {
        return target;
    }
}
