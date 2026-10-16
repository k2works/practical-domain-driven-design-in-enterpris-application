package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;
import java.util.Set;

/**
 * 利用者の詳細。S-20 の詳細の読み出しの射影（Bolt 34）。一覧の行の項目に、所属企業の ID と種類と版を足したもの。`password_hash` を
 * 持たない。
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
        long version) {

    public UserDetail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(companyName, "companyName");
        Objects.requireNonNull(companyKind, "companyKind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(roles);
    }
}
