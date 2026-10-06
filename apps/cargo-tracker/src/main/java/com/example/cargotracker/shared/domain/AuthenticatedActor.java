package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 認証された利用者。ログインした利用者の利用者 ID・企業 ID・役割・表示名・企業名。
 * ほかのコンテキストは、Spring Security の型にも identity にも依存せず、この型だけで操作者と所属企業を知る（ADR-012）。
 *
 * @param userId 利用者 ID
 * @param companyId 所属企業 ID
 * @param roles 役割（1 つ以上）
 * @param displayName 表示名
 * @param companyName 所属企業の名前
 */
@ValueObject
public record AuthenticatedActor(
        UserId userId, CompanyId companyId, Set<Role> roles, String displayName, String companyName) {

    public AuthenticatedActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(companyName, "companyName");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("役割が 1 つ以上要る");
        }
        roles = Set.copyOf(EnumSet.copyOf(roles));
    }

    /** その役割を持つか。 */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
