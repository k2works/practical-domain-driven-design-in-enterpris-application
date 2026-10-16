package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Set;

/**
 * 管理範囲。システム管理者が扱える企業の集合（Bolt 34b。`user_assignment` の `COMPANY` の行。IA-INV-14）。対象の利用者の所属企業を
 * 含むかを判定する。判定は所属企業だけで見て、自分の詳細にも例外を作らない（Bolt 34b の確認ポイント 5）。
 *
 * @param companies 管理する企業
 */
@ValueObject
public record AdministrationScope(Set<CompanyId> companies) {

    public AdministrationScope {
        companies = Set.copyOf(companies);
    }

    /** その企業を管理範囲に含むか。 */
    public boolean covers(CompanyId company) {
        return companies.contains(company);
    }
}
