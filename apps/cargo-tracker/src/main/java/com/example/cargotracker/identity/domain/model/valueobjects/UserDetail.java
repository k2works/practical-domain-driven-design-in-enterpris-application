package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 利用者の詳細。S-20 の詳細の読み出しの射影（Bolt 34）。一覧の行の項目に、所属企業の ID と種類と版を足したもの。`password_hash` を
 * 持たない。Bolt 38c から、ログインの資格（ロックの期限と認証アプリの登録の時刻）を持つ。TOTP の秘密は持たない。Bolt 38c2 から、停止中の
 * 利用者の今の停止の記録を持つ（照会のサービスが {@link #asOf} の後に {@link #withSuspension} で載せる）。Bolt 38e から、担当範囲のある役割の
 * 利用者の担当企業を持つ（照会のサービスが {@link #withAssignedCompanies} で載せる。ほかの利用者は空）。
 *
 * @param id 利用者 ID
 * @param companyId 所属企業 ID
 * @param companyName 所属企業の名前
 * @param companyKind 所属企業の種類
 * @param displayName 表示名
 * @param email メールアドレス
 * @param status 利用状態
 * @param roles 役割
 * @param version 版（役割の変更の期待する版）
 * @param lockedUntil ロックの期限（ロックの記録がなければ空。照会のサービスが {@link #asOf} で、期限の切れたロックを空にする）
 * @param totpEnrolledAt 認証アプリの登録の時刻（未登録なら空。登録の有無と、解除の確認の画面で持つ値を兼ねる）
 * @param suspension 今の停止の記録（利用中の利用者と、停止中で記録のない利用者は空）
 * @param assignedCompanies 担当企業（管理範囲の外を含む全体。担当範囲のない役割の利用者は空）
 */
@ValueObject
public record UserDetail(
        UserId id,
        CompanyId companyId,
        String companyName,
        CompanyKind companyKind,
        String displayName,
        EmailAddress email,
        UserStatus status,
        Set<Role> roles,
        long version,
        Optional<UtcInstant> lockedUntil,
        Optional<UtcInstant> totpEnrolledAt,
        Optional<SuspensionRecord> suspension,
        AssignedCompanies assignedCompanies) {

    public UserDetail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(companyName, "companyName");
        Objects.requireNonNull(companyKind, "companyKind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(roles);
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(totpEnrolledAt, "totpEnrolledAt");
        Objects.requireNonNull(suspension, "suspension");
        Objects.requireNonNull(assignedCompanies, "assignedCompanies");
    }

    /**
     * 停止の記録と担当企業を持たない詳細（リポジトリが読む形。照会のサービスが {@link #withSuspension}・{@link #withAssignedCompanies}
     * で載せる）。既存の詳細の作り直しには使わない（停止の記録と担当企業が黙って落ちる）。作り直しは {@link #asOf}・
     * {@link #withSuspension}・{@link #withAssignedCompanies} で行う。
     */
    @SuppressWarnings("java:S107") // 射影の項目をそろえて受ける
    public UserDetail(
            UserId id,
            CompanyId companyId,
            String companyName,
            CompanyKind companyKind,
            String displayName,
            EmailAddress email,
            UserStatus status,
            Set<Role> roles,
            long version,
            Optional<UtcInstant> lockedUntil,
            Optional<UtcInstant> totpEnrolledAt) {
        this(
                id,
                companyId,
                companyName,
                companyKind,
                displayName,
                email,
                status,
                roles,
                version,
                lockedUntil,
                totpEnrolledAt,
                Optional.empty(),
                AssignedCompanies.none());
    }

    /** 今の停止の記録を載せた詳細（Bolt 38c2）。 */
    public UserDetail withSuspension(Optional<SuspensionRecord> latest) {
        return new UserDetail(
                id,
                companyId,
                companyName,
                companyKind,
                displayName,
                email,
                status,
                roles,
                version,
                lockedUntil,
                totpEnrolledAt,
                latest,
                assignedCompanies);
    }

    /** 担当企業を載せた詳細（Bolt 38e）。 */
    public UserDetail withAssignedCompanies(AssignedCompanies assigned) {
        return new UserDetail(
                id,
                companyId,
                companyName,
                companyKind,
                displayName,
                email,
                status,
                roles,
                version,
                lockedUntil,
                totpEnrolledAt,
                suspension,
                assigned);
    }

    /** その時刻の詳細。ロックの期限がその時刻か、それより前なら（ロックは解けている）、ロックの期限を空にする（期限ちょうどで解ける）。 */
    public UserDetail asOf(UtcInstant now) {
        if (lockedUntil.isEmpty() || now.instant().isBefore(lockedUntil.get().instant())) {
            return this;
        }
        return new UserDetail(
                id,
                companyId,
                companyName,
                companyKind,
                displayName,
                email,
                status,
                roles,
                version,
                Optional.empty(),
                totpEnrolledAt,
                suspension,
                assignedCompanies);
    }
}
