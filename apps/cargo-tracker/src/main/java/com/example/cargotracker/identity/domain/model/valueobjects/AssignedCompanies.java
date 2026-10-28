package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.HashSet;
import java.util.Set;

/**
 * 担当企業。業務担当者が業務の画面で扱える企業の集合（Bolt 38e。`user_assignment` の `COMPANY` の行。IA-INV-02）。システム管理者の
 * 管理範囲（{@link AdministrationScope}）と同じ行の形を使うが、意味が違うので型を分ける（管理範囲は「管理できる企業」、担当企業は「業務で
 * 扱える企業」）。担当案件は担当企業で近似する（パイロット。Bolt 38e の決定 (10)）。業務の画面で効くのは Bolt 38f・38g から。
 *
 * @param companies 担当する企業（空を許す）
 */
@ValueObject
public record AssignedCompanies(Set<CompanyId> companies) {

    private static final AssignedCompanies NONE = new AssignedCompanies(Set.of());

    public AssignedCompanies {
        companies = Set.copyOf(companies);
    }

    /** 担当企業のない状態。 */
    public static AssignedCompanies none() {
        return NONE;
    }

    /** その企業を担当するか。 */
    public boolean contains(CompanyId company) {
        return companies.contains(company);
    }

    /** 範囲の中の担当企業。 */
    public AssignedCompanies within(AdministrationScope scope) {
        Set<CompanyId> inside = new HashSet<>(companies);
        inside.removeIf(company -> !scope.covers(company));
        return new AssignedCompanies(inside);
    }

    /**
     * 範囲の外の担当企業の数（画面は名前を出さず数だけを示す。Bolt 38e の決定 (11)）。S-20 は担当企業にできる企業の範囲を渡し、この画面で
     * 変えられない担当企業（管理範囲の外の企業と、管理範囲の中でも無効になった荷主など）を数える。
     */
    public int outsideCountOf(AdministrationScope scope) {
        return companies.size() - within(scope).companies().size();
    }

    /** 範囲の中を選んだ企業に置き換えた担当企業。範囲の外の担当企業はそのまま残す（範囲の中だけを変える）。 */
    public AssignedCompanies replaceWithin(AdministrationScope scope, AssignedCompanies selected) {
        Set<CompanyId> replaced = new HashSet<>(companies);
        replaced.removeIf(scope::covers);
        replaced.addAll(selected.companies());
        return new AssignedCompanies(replaced);
    }
}
