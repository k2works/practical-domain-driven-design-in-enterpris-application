package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 役割の割り当ての規則。役割の組合せを確かめる（Bolt 34。BR-15、IA-INV-01、IA-INV-13、UC-16 の主シナリオ 4）。
 * 職務分離: システム管理者・監査担当者と、業務データを変更できる 6 つの役割は併せて付けない。システム管理者と監査担当者の併用は、
 * BR-15 の文言（「業務データを変更できない」）どおり拒まない（Bolt 34 の確認ポイント 6）。
 * 企業間分離: 荷主担当者は荷主企業の利用者だけ、ほかの 7 役割は A 社の利用者だけに付ける。荷受人の企業の利用者には BR-15 の役割を
 * 付けない（荷受人は参照許可。US-19）。
 * 監督の役割（システム管理者・監査担当者）は画面で付け外ししない（Bolt 34 の開発レビューの決定。2 人目の承認の仕組みができるまでは
 * 運用の手順で行う）。
 * 共有カーネルの {@link Role} は変えず、BR-15 の規則として identity に置く。
 */
@DomainRule
public final class RoleAssignmentPolicy {

    /** 業務データを変更できる役割。 */
    private static final Set<Role> BUSINESS_ROLES = EnumSet.of(
            Role.SHIPPER,
            Role.SALES,
            Role.ROUTE_DESIGNER,
            Role.TRACKING_MANAGER,
            Role.DATA_STEWARD,
            Role.CUSTOMER_SUPPORT);

    /** 業務データを変更できない役割（業務の役割と併せて付けない）。 */
    private static final Set<Role> OVERSIGHT_ROLES = EnumSet.of(Role.SYSTEM_ADMIN, Role.AUDITOR);

    /** 企業の種類ごとに付けられる役割。 */
    private static final Map<CompanyKind, Set<Role>> FIT_ROLES = Map.of(
            CompanyKind.SHIPPER, EnumSet.of(Role.SHIPPER),
            CompanyKind.OPERATOR, EnumSet.complementOf(EnumSet.of(Role.SHIPPER)),
            CompanyKind.CONSIGNEE, EnumSet.noneOf(Role.class));

    private RoleAssignmentPolicy() {}

    /** 職務分離で競合する役割を返す。競合がなければ空。業務の役割と監督の役割の両方を持つときだけ、その両方を返す。 */
    public static Set<Role> conflicts(Set<Role> roles) {
        Set<Role> business = only(roles, BUSINESS_ROLES);
        Set<Role> oversight = only(roles, OVERSIGHT_ROLES);
        if (business.isEmpty() || oversight.isEmpty()) {
            return Set.of();
        }
        business.addAll(oversight);
        return Set.copyOf(business);
    }

    /** その企業の種類の利用者に付けられない役割を返す。すべて付けられるなら空。 */
    public static Set<Role> unfitRoles(CompanyKind companyKind, Set<Role> roles) {
        Set<Role> unfit = EnumSet.noneOf(Role.class);
        unfit.addAll(roles);
        unfit.removeAll(FIT_ROLES.get(companyKind));
        return Set.copyOf(unfit);
    }

    /** 今の役割から新しい役割への変更で、足すか外す監督の役割を返す。なければ空。 */
    public static Set<Role> oversightChanges(Set<Role> current, Set<Role> next) {
        Set<Role> changed = only(current, OVERSIGHT_ROLES);
        Set<Role> added = only(next, OVERSIGHT_ROLES);
        Set<Role> kept = EnumSet.noneOf(Role.class);
        kept.addAll(changed);
        kept.retainAll(added);
        changed.addAll(added);
        changed.removeAll(kept);
        return Set.copyOf(changed);
    }

    /** 業務データを変更できる役割だけを返す（職務分離の文言で示す役割）。 */
    public static Set<Role> businessRolesOf(Set<Role> roles) {
        return Set.copyOf(only(roles, BUSINESS_ROLES));
    }

    /** その企業の種類の利用者に画面で付けられる役割（企業の種類に合う業務の役割）。 */
    public static Set<Role> assignableRoles(CompanyKind companyKind) {
        return Set.copyOf(only(FIT_ROLES.get(companyKind), BUSINESS_ROLES));
    }

    private static Set<Role> only(Set<Role> roles, Set<Role> kinds) {
        Set<Role> result = EnumSet.noneOf(Role.class);
        roles.stream().filter(kinds::contains).forEach(result::add);
        return result;
    }
}
