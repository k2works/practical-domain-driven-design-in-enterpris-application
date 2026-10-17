package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 利用者の認証の前提の規則（IA-INV-09、US-18 AC4）。
 */
class UserTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());

    private static Company company(boolean active) {
        return Company.of(COMPANY, "荷主 A", CompanyKind.SHIPPER, active);
    }

    private static User user(UserStatus status) {
        return User.of(
                new UserId(UUID.randomUUID()),
                COMPANY,
                EmailAddress.of("shipper@example.com"),
                "荷主 太郎",
                "{bcrypt}$2a$10$hash",
                status,
                Set.of(Role.SHIPPER));
    }

    @Test
    void 有効な企業の利用中の利用者は認証できる() {
        assertThat(user(UserStatus.ACTIVE).authenticationRejection(company(true)))
                .isEmpty();
    }

    @Test
    void 利用停止の利用者は認証できない() {
        assertThat(user(UserStatus.SUSPENDED).authenticationRejection(company(true)))
                .contains(AuthenticationRejection.SUSPENDED);
    }

    @Test
    void 無効な企業の利用者は認証できない() {
        assertThat(user(UserStatus.ACTIVE).authenticationRejection(company(false)))
                .contains(AuthenticationRejection.COMPANY_INACTIVE);
    }

    @Test
    void 利用停止で企業も無効なら利用停止を理由にする() {
        assertThat(user(UserStatus.SUSPENDED).authenticationRejection(company(false)))
                .contains(AuthenticationRejection.SUSPENDED);
    }

    @Test
    void 所属していない企業では確かめられない() {
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "荷主 B", CompanyKind.SHIPPER, true);
        User user = user(UserStatus.ACTIVE);

        assertThatThrownBy(() -> user.authenticationRejection(other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 役割のない利用者は作れない() {
        UserId id = new UserId(UUID.randomUUID());
        EmailAddress email = EmailAddress.of("norole@example.com");
        Set<Role> noRoles = Set.of();

        assertThatThrownBy(() -> User.of(id, COMPANY, email, "役割なし", "{bcrypt}$2a$10$hash", UserStatus.ACTIVE, noRoles))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passwordを変えた利用者はほかの値を保ち新しいハッシュを持つ() {
        User before = user(UserStatus.ACTIVE);

        User after = before.changePassword("{bcrypt}$2a$10$newhash");

        assertThat(after.passwordHash()).isEqualTo("{bcrypt}$2a$10$newhash");
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.email()).isEqualTo(before.email());
        assertThat(after.roles()).isEqualTo(before.roles());
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(before.passwordHash()).as("元の利用者は変えない").isNotEqualTo("{bcrypt}$2a$10$newhash");
    }

    // 役割の変更（Bolt 34。US-16 AC3、UC-16 の主シナリオ 4）

    private static final UserId ADMIN = new UserId(UUID.randomUUID());

    private static final Company OPERATOR = Company.of(COMPANY, "A 社", CompanyKind.OPERATOR, true);

    private static User staff(Set<Role> roles) {
        return User.of(
                new UserId(UUID.randomUUID()),
                COMPANY,
                EmailAddress.of("staff@example.com"),
                "営業 一郎",
                "{bcrypt}$2a$10$hash",
                UserStatus.ACTIVE,
                roles,
                4L);
    }

    @Test
    void 復元した版を持つ() {
        assertThat(staff(Set.of(Role.SALES)).version()).isEqualTo(4L);
    }

    @Test
    void 版を渡さずに組み立てた利用者の版は0() {
        assertThat(user(UserStatus.ACTIVE).version()).isZero();
    }

    @Test
    void 役割を足せる() {
        User sales = staff(Set.of(Role.SALES));

        assertThat(sales.roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES, Role.ROUTE_DESIGNER)))
                .isEmpty();
        assertThat(sales.changeRoles(Set.of(Role.SALES, Role.ROUTE_DESIGNER)).roles())
                .containsExactlyInAnyOrder(Role.SALES, Role.ROUTE_DESIGNER);
    }

    @Test
    void 役割を変えても版とほかの項目は変えない() {
        User sales = staff(Set.of(Role.SALES));

        User changed = sales.changeRoles(Set.of(Role.ROUTE_DESIGNER));

        assertThat(changed.version()).isEqualTo(4L);
        assertThat(changed.id()).isEqualTo(sales.id());
        assertThat(changed.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 自分の役割は変えられない() {
        User admin = User.of(
                ADMIN,
                COMPANY,
                EmailAddress.of("admin@example.com"),
                "管理 次郎",
                "{bcrypt}$2a$10$hash",
                UserStatus.ACTIVE,
                Set.of(Role.SYSTEM_ADMIN),
                0L);

        assertThat(admin.roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SYSTEM_ADMIN, Role.AUDITOR)))
                .map(RoleChangeRejection::reason)
                .contains(RoleChangeRejectionReason.SELF_CHANGE);
    }

    @Test
    void 役割を1つも選ばない変更は拒む() {
        assertThat(staff(Set.of(Role.SALES)).roleChangeRejection(ADMIN, OPERATOR, Set.of()))
                .map(RoleChangeRejection::reason)
                .contains(RoleChangeRejectionReason.NO_ROLES);
    }

    @Test
    void 今と同じ役割の変更は拒む() {
        assertThat(staff(Set.of(Role.SALES)).roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES)))
                .map(RoleChangeRejection::reason)
                .contains(RoleChangeRejectionReason.NO_CHANGE);
    }

    @Test
    void 企業の種類に合わない役割は拒み合わない役割を示す() {
        assertThat(staff(Set.of(Role.SALES)).roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES, Role.SHIPPER)))
                .contains(new RoleChangeRejection(RoleChangeRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SHIPPER)));
    }

    @Test
    void 職務分離で競合する役割は拒み競合する役割を示す() {
        // 監査担当者に業務の役割を足す（監督の役割は残すので監督の変更には当たらない）
        assertThat(staff(Set.of(Role.AUDITOR)).roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.AUDITOR, Role.SALES)))
                .contains(new RoleChangeRejection(
                        RoleChangeRejectionReason.CONFLICTING_ROLES, Set.of(Role.SALES, Role.AUDITOR)));
    }

    @Test
    void 監督の役割を足す変更は拒む() {
        assertThat(staff(Set.of(Role.SALES))
                        .roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES, Role.SYSTEM_ADMIN)))
                .contains(new RoleChangeRejection(RoleChangeRejectionReason.OVERSIGHT_ROLE, Set.of(Role.SYSTEM_ADMIN)));
    }

    @Test
    void 監督の役割を外す変更は拒む() {
        assertThat(staff(Set.of(Role.SYSTEM_ADMIN)).roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES)))
                .map(RoleChangeRejection::reason)
                .contains(RoleChangeRejectionReason.OVERSIGHT_ROLE);
    }

    @Test
    void 役割を外すだけの変更は受け付ける() {
        assertThat(staff(Set.of(Role.SALES, Role.ROUTE_DESIGNER))
                        .roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.SALES)))
                .isEmpty();
    }

    @Test
    void 企業の種類を職務分離より先に判定する() {
        assertThat(staff(Set.of(Role.AUDITOR))
                        .roleChangeRejection(ADMIN, OPERATOR, Set.of(Role.AUDITOR, Role.SHIPPER, Role.SALES)))
                .map(RoleChangeRejection::reason)
                .contains(RoleChangeRejectionReason.UNFIT_FOR_COMPANY);
    }

    @Test
    void 役割の変更は所属企業で確かめる() {
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        User sales = staff(Set.of(Role.SALES));
        Set<Role> roles = Set.of(Role.ROUTE_DESIGNER);

        assertThatThrownBy(() -> sales.roleChangeRejection(ADMIN, other, roles))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 利用停止と再開（Bolt 34d。US-16 AC5、UC-16 の 5a）

    private static final Optional<SuspensionReason> TRANSFERRED = Optional.of(SuspensionReason.TRANSFERRED);

    private static User staff(Set<Role> roles, UserStatus status) {
        return User.of(
                new UserId(UUID.randomUUID()),
                COMPANY,
                EmailAddress.of("staff@example.com"),
                "営業 一郎",
                "{bcrypt}$2a$10$hash",
                status,
                roles,
                4L);
    }

    @Test
    void 利用中の利用者を理由を付けて停止できる() {
        assertThat(staff(Set.of(Role.SALES), UserStatus.ACTIVE)
                        .statusChangeRejection(ADMIN, UserStatus.SUSPENDED, TRANSFERRED))
                .isEmpty();
    }

    @Test
    void 停止中の利用者を再開できる() {
        assertThat(staff(Set.of(Role.SALES), UserStatus.SUSPENDED)
                        .statusChangeRejection(ADMIN, UserStatus.ACTIVE, Optional.empty()))
                .isEmpty();
    }

    @Test
    void 自分の利用状態は変えられない() {
        User admin = User.of(
                ADMIN,
                COMPANY,
                EmailAddress.of("admin@example.com"),
                "管理 次郎",
                "{bcrypt}$2a$10$hash",
                UserStatus.ACTIVE,
                Set.of(Role.SYSTEM_ADMIN),
                0L);

        assertThat(admin.statusChangeRejection(ADMIN, UserStatus.SUSPENDED, TRANSFERRED))
                .contains(StatusChangeRejection.SELF_CHANGE);
    }

    @ParameterizedTest(name = "{0} を持つ利用者は止められない")
    @EnumSource(
            value = Role.class,
            names = {"SYSTEM_ADMIN", "AUDITOR"})
    void 監督の役割を持つ利用者は止められない(Role oversight) {
        assertThat(staff(Set.of(oversight), UserStatus.ACTIVE)
                        .statusChangeRejection(ADMIN, UserStatus.SUSPENDED, TRANSFERRED))
                .contains(StatusChangeRejection.OVERSIGHT_ROLE);
    }

    @Test
    void 監督の役割を持つ停止中の利用者は再開できない() {
        assertThat(staff(Set.of(Role.AUDITOR), UserStatus.SUSPENDED)
                        .statusChangeRejection(ADMIN, UserStatus.ACTIVE, Optional.empty()))
                .contains(StatusChangeRejection.OVERSIGHT_ROLE);
    }

    @Test
    void 今と同じ利用状態への変更は拒む() {
        assertThat(staff(Set.of(Role.SALES), UserStatus.SUSPENDED)
                        .statusChangeRejection(ADMIN, UserStatus.SUSPENDED, TRANSFERRED))
                .contains(StatusChangeRejection.NO_CHANGE);
        assertThat(staff(Set.of(Role.SALES), UserStatus.ACTIVE)
                        .statusChangeRejection(ADMIN, UserStatus.ACTIVE, Optional.empty()))
                .contains(StatusChangeRejection.NO_CHANGE);
    }

    @Test
    void 理由のない停止は拒む() {
        assertThat(staff(Set.of(Role.SALES), UserStatus.ACTIVE)
                        .statusChangeRejection(ADMIN, UserStatus.SUSPENDED, Optional.empty()))
                .contains(StatusChangeRejection.REASON_REQUIRED);
    }

    @Test
    void 監督の役割を同じ状態より先に判定する() {
        // 監督の役割の利用者の今の状態を、拒否の種類で区別しない（決定 4）
        assertThat(staff(Set.of(Role.SYSTEM_ADMIN), UserStatus.ACTIVE)
                        .statusChangeRejection(ADMIN, UserStatus.ACTIVE, Optional.empty()))
                .contains(StatusChangeRejection.OVERSIGHT_ROLE);
    }

    @Test
    void 同じ状態を理由より先に判定する() {
        assertThat(staff(Set.of(Role.SALES), UserStatus.SUSPENDED)
                        .statusChangeRejection(ADMIN, UserStatus.SUSPENDED, Optional.empty()))
                .contains(StatusChangeRejection.NO_CHANGE);
    }

    @Test
    void 自分を監督の役割より先に判定する() {
        User admin = User.of(
                ADMIN,
                COMPANY,
                EmailAddress.of("admin@example.com"),
                "管理 次郎",
                "{bcrypt}$2a$10$hash",
                UserStatus.ACTIVE,
                Set.of(Role.SYSTEM_ADMIN),
                0L);

        assertThat(admin.statusChangeRejection(ADMIN, UserStatus.SUSPENDED, Optional.empty()))
                .contains(StatusChangeRejection.SELF_CHANGE);
    }

    @Test
    void 利用状態を変えても版とほかの項目は変えない() {
        User sales = staff(Set.of(Role.SALES), UserStatus.ACTIVE);

        User suspended = sales.changeStatus(UserStatus.SUSPENDED);

        assertThat(suspended.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(suspended.version()).isEqualTo(4L);
        assertThat(suspended.id()).isEqualTo(sales.id());
        assertThat(suspended.roles()).isEqualTo(sales.roles());
        assertThat(suspended.passwordHash()).isEqualTo(sales.passwordHash());
        assertThat(sales.status()).as("元の利用者は変えない").isEqualTo(UserStatus.ACTIVE);
    }
}
