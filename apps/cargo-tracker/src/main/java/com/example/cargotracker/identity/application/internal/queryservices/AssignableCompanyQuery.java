package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 担当企業にできる企業（操作者の管理範囲の中の有効な荷主。{@link Company#acceptsAssignment}）を読む 1 つの口（Bolt 38e の開発レビューの
 * 中-3）。画面に並べる選択肢（照会 {@link UserAdministrationQueryService#assignableCompanies}）と、確定で受け付けて置き換えてよい範囲
 * （担当範囲の変更のサービス）の両方がここを呼び、片方だけが変わってずれることを防ぐ。Bean にせず、使う側が組み立てる（前例
 * {@code ManagedUserLookup}）。
 */
public final class AssignableCompanyQuery {

    private final CompanyRepository companies;
    private final UserAssignmentRepository assignments;

    public AssignableCompanyQuery(CompanyRepository companies, UserAssignmentRepository assignments) {
        this.companies = Objects.requireNonNull(companies, "companies");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
    }

    /**
     * 操作者が担当企業にできる企業を、リポジトリの順（名前・ID）で返す。
     *
     * @param operator 操作者（システム管理者であることは呼ぶ側が確かめる）
     */
    public List<Company> of(UserId operator) {
        return companies.findAllIn(assignments.scopeOf(operator).companies()).stream()
                .filter(Company::acceptsAssignment)
                .toList();
    }

    /**
     * 担当企業にできる企業の範囲（置き換えてよい範囲と、この画面で変えられない担当企業の数え方に使う）。
     *
     * @param assignable {@link #of} の結果
     */
    public static AdministrationScope scopeOf(List<Company> assignable) {
        return new AdministrationScope(assignable.stream().map(Company::id).collect(Collectors.toUnmodifiableSet()));
    }
}
