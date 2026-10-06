package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 監査記録。操作者・時刻・操作・結果・理由の追記専用の記録（IA-INV-07）。
 * Bolt 14 はログインの成功・失敗とログアウトを、同じ request の中で同期に書く（IA-INV-08）。
 * 対象・変更前後・承認・出典の項目は使う Bolt で足す。
 */
@AggregateRoot
public final class AuditRecord {

    private final UUID id;
    private final UtcInstant occurredAt;
    private final UserId actorUserId;
    private final CompanyId actorCompanyId;
    private final AuditAction action;
    private final AuditResult result;
    private final String reason;

    private AuditRecord(
            UUID id,
            UtcInstant occurredAt,
            UserId actorUserId,
            CompanyId actorCompanyId,
            AuditAction action,
            AuditResult result,
            String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorUserId = actorUserId;
        this.actorCompanyId = actorCompanyId;
        this.action = Objects.requireNonNull(action, "action");
        this.result = Objects.requireNonNull(result, "result");
        this.reason = reason;
    }

    /** ログインの成功を記録する。 */
    public static AuditRecord loginSucceeded(UserId user, CompanyId company, UtcInstant at) {
        return null;
    }

    /**
     * ログインの失敗を記録する。利用者が分からない（存在しないメールアドレス）ときは、利用者と企業を null にする。
     */
    public static AuditRecord loginFailed(
            UserId user, CompanyId company, AuthenticationRejection reason, UtcInstant at) {
        return null;
    }

    /** ログアウトを記録する。 */
    public static AuditRecord logout(UserId user, CompanyId company, UtcInstant at) {
        return null;
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    public static AuditRecord reconstitute(
            UUID id,
            UtcInstant occurredAt,
            UserId actorUserId,
            CompanyId actorCompanyId,
            AuditAction action,
            AuditResult result,
            String reason) {
        return new AuditRecord(id, occurredAt, actorUserId, actorCompanyId, action, result, reason);
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
}
