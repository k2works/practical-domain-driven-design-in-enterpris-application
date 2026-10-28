package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 認証された利用者。ログインした利用者の利用者 ID・企業 ID・役割・表示名・企業名・担当企業。
 * ほかのコンテキストは、Spring Security の型にも identity にも依存せず、この型だけで操作者と所属企業と担当企業を知る（ADR-012）。
 * 担当企業は業務担当者が業務の画面で扱える企業で（Bolt 38e の割り当て）、request ごとの確かめで今の値が載る（Bolt 38f）。判定は
 * {@link #handlesCompany} の 1 か所に置き、担当企業がなければどの企業も扱わない（失敗に倒す。空を全件として読まない）。
 *
 * @param userId 利用者 ID
 * @param companyId 所属企業 ID
 * @param roles 役割（1 つ以上）
 * @param displayName 表示名
 * @param companyName 所属企業の名前
 * @param assignedCompanies 担当企業（空を許す。対象の役割のない利用者と、確かめの前の主体は空）
 */
@ValueObject
public record AuthenticatedActor(
        UserId userId,
        CompanyId companyId,
        Set<Role> roles,
        String displayName,
        String companyName,
        Set<CompanyId> assignedCompanies) {

    public AuthenticatedActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(companyName, "companyName");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("役割が 1 つ以上要る");
        }
        roles = Set.copyOf(EnumSet.copyOf(roles));
        assignedCompanies = Set.copyOf(Objects.requireNonNull(assignedCompanies, "assignedCompanies"));
    }

    /** 担当企業を持たない主体（荷主・システム管理者、テストの準備。担当企業は空）。 */
    public AuthenticatedActor(
            UserId userId, CompanyId companyId, Set<Role> roles, String displayName, String companyName) {
        this(userId, companyId, roles, displayName, companyName, Set.of());
    }

    /** その役割を持つか。 */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** その企業を担当するか。担当企業に含むときだけ真（空ならどの企業も扱わない。Bolt 38f の決定 (6)）。 */
    public boolean handlesCompany(CompanyId company) {
        return assignedCompanies.contains(company);
    }

    /** 担当企業がないか（社内の一覧の案内の文に使う。Bolt 38f）。 */
    public boolean hasNoAssignedCompanies() {
        return assignedCompanies.isEmpty();
    }
}
