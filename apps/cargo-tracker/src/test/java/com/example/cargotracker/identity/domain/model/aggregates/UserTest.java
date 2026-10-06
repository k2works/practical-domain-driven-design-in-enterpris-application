package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
