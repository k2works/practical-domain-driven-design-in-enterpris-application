package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;
import java.util.Set;

/**
 * 利用者の一覧の行。S-20 の一覧の 1 行の読み出しの射影（Bolt 34）。`password_hash` を持たない。
 *
 * @param id 利用者 ID
 * @param companyName 所属企業の名前
 * @param displayName 表示名
 * @param email メールアドレス
 * @param status 利用状態
 * @param roles 役割
 */
@ValueObject
public record UserSummary(
        UserId id, String companyName, String displayName, EmailAddress email, UserStatus status, Set<Role> roles) {

    public UserSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyName, "companyName");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(roles);
    }
}
