package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.domain.Role;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 役割の割り当ての規則（Bolt 34。IA-INV-01 の職務分離と IA-INV-13 の企業の種類）。 */
class RoleAssignmentPolicyTest {

    private static final Set<Role> BUSINESS_ROLES = EnumSet.of(
            Role.SHIPPER,
            Role.SALES,
            Role.ROUTE_DESIGNER,
            Role.TRACKING_MANAGER,
            Role.DATA_STEWARD,
            Role.CUSTOMER_SUPPORT);

    @ParameterizedTest(name = "{0} とシステム管理者は競合する")
    @EnumSource(
            value = Role.class,
            names = {"SHIPPER", "SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER", "DATA_STEWARD", "CUSTOMER_SUPPORT"})
    void システム管理者と業務の役割は競合する(Role business) {
        assertThat(RoleAssignmentPolicy.conflicts(Set.of(Role.SYSTEM_ADMIN, business)))
                .containsExactlyInAnyOrder(Role.SYSTEM_ADMIN, business);
    }

    @ParameterizedTest(name = "{0} と監査担当者は競合する")
    @EnumSource(
            value = Role.class,
            names = {"SHIPPER", "SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER", "DATA_STEWARD", "CUSTOMER_SUPPORT"})
    void 監査担当者と業務の役割は競合する(Role business) {
        assertThat(RoleAssignmentPolicy.conflicts(Set.of(Role.AUDITOR, business)))
                .containsExactlyInAnyOrder(Role.AUDITOR, business);
    }

    @Test
    void システム管理者と監査担当者の併用は競合しない() {
        // BR-15 が禁じるのは業務データを変更できる役割との併用だけ（Bolt 34 の確認ポイント 6）
        assertThat(RoleAssignmentPolicy.conflicts(Set.of(Role.SYSTEM_ADMIN, Role.AUDITOR)))
                .isEmpty();
    }

    @Test
    void 業務の役割どうしは競合しない() {
        assertThat(RoleAssignmentPolicy.conflicts(BUSINESS_ROLES)).isEmpty();
    }

    @Test
    void 競合する役割をすべて示す() {
        assertThat(RoleAssignmentPolicy.conflicts(
                        Set.of(Role.SYSTEM_ADMIN, Role.AUDITOR, Role.SALES, Role.ROUTE_DESIGNER)))
                .containsExactlyInAnyOrder(Role.SYSTEM_ADMIN, Role.AUDITOR, Role.SALES, Role.ROUTE_DESIGNER);
    }

    @Test
    void 荷主企業には荷主担当者だけを付けられる() {
        assertThat(RoleAssignmentPolicy.unfitRoles(CompanyKind.SHIPPER, EnumSet.allOf(Role.class)))
                .containsExactlyInAnyOrderElementsOf(EnumSet.complementOf(EnumSet.of(Role.SHIPPER)));
    }

    @Test
    void A社には荷主担当者のほかの7役割を付けられる() {
        assertThat(RoleAssignmentPolicy.unfitRoles(CompanyKind.OPERATOR, EnumSet.allOf(Role.class)))
                .containsExactly(Role.SHIPPER);
    }

    @ParameterizedTest(name = "荷受人の企業には {0} を付けられない")
    @EnumSource(Role.class)
    void 荷受人の企業にはどの役割も付けられない(Role role) {
        assertThat(RoleAssignmentPolicy.unfitRoles(CompanyKind.CONSIGNEE, Set.of(role)))
                .containsExactly(role);
    }

    @Test
    void 企業の種類に合う役割だけなら空() {
        assertThat(RoleAssignmentPolicy.unfitRoles(CompanyKind.SHIPPER, Set.of(Role.SHIPPER)))
                .isEmpty();
        assertThat(RoleAssignmentPolicy.unfitRoles(CompanyKind.OPERATOR, Set.of(Role.SALES, Role.ROUTE_DESIGNER)))
                .isEmpty();
    }
}
