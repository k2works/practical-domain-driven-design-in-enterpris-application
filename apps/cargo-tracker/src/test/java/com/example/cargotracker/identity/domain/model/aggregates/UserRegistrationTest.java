package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.User.UserRegistrationDecision;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 利用者の登録の判定と生成（Bolt 38b。US-16 AC1・AC3、UC-16 の主シナリオ 1）。判定は役割なし → 監督の役割 → 企業の種類 → 職務分離の順で、
 * 受け付けたときの承認の値からだけ利用者を作る。
 */
class UserRegistrationTest {

    private static final Company OPERATOR = company(CompanyKind.OPERATOR);
    private static final Company SHIPPER = company(CompanyKind.SHIPPER);
    private static final Company CONSIGNEE = company(CompanyKind.CONSIGNEE);
    private static final UserId NEW_USER = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000901"));

    @Test
    void 運用する会社に営業担当者を登録すると承認から版0の利用中の利用者を作る() {
        // 行 1
        User registered = register(OPERATOR, Set.of(Role.SALES));

        assertThat(registered.id()).isEqualTo(NEW_USER);
        assertThat(registered.companyId()).isEqualTo(OPERATOR.id());
        assertThat(registered.roles()).containsExactly(Role.SALES);
        assertThat(registered.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(registered.version()).isZero();
        assertThat(registered.displayName()).isEqualTo("新人 花子");
        assertThat(registered.email()).isEqualTo(new EmailAddress("hanako@example.com"));
    }

    @Test
    void 荷主の企業に荷主担当者を登録できる() {
        // 行 1
        assertThat(register(SHIPPER, Set.of(Role.SHIPPER)).roles()).containsExactly(Role.SHIPPER);
    }

    @Test
    void 役割がなければ拒む() {
        // 行 2
        assertThat(refusal(OPERATOR, Set.of()))
                .isEqualTo(new UserRegistrationRejection(UserRegistrationRejectionReason.NO_ROLES, Set.of()));
    }

    @Test
    void 監督の役割は登録でも付けない() {
        // 行 3（IA-INV-01。運用する会社の付けられる役割には監督の役割も入るので、企業の種類の判定では止まらない）
        assertThat(refusal(OPERATOR, Set.of(Role.SYSTEM_ADMIN)))
                .isEqualTo(new UserRegistrationRejection(
                        UserRegistrationRejectionReason.OVERSIGHT_ROLE, Set.of(Role.SYSTEM_ADMIN)));
        assertThat(refusal(OPERATOR, Set.of(Role.AUDITOR, Role.SALES)))
                .isEqualTo(new UserRegistrationRejection(
                        UserRegistrationRejectionReason.OVERSIGHT_ROLE, Set.of(Role.AUDITOR)));
    }

    @Test
    void 企業の種類に合わない役割を拒む() {
        // 行 4（IA-INV-13。荷受人の企業にはどの業務の役割も付けない）
        assertThat(refusal(SHIPPER, Set.of(Role.SALES, Role.SHIPPER)))
                .isEqualTo(new UserRegistrationRejection(
                        UserRegistrationRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SALES)));
        assertThat(refusal(CONSIGNEE, Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationRejection(
                        UserRegistrationRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SHIPPER)));
    }

    @Test
    void 監督の役割は企業の種類より先に拒む() {
        // 行 5（判定の順）
        assertThat(refusal(CONSIGNEE, Set.of(Role.SYSTEM_ADMIN, Role.SHIPPER)).reason())
                .isEqualTo(UserRegistrationRejectionReason.OVERSIGHT_ROLE);
    }

    private static User register(Company company, Set<Role> roles) {
        UserRegistrationDecision decision = User.decideRegistration(company, roles);
        assertThat(decision).isInstanceOf(UserRegistrationDecision.Approved.class);
        return User.register(
                ((UserRegistrationDecision.Approved) decision).approval(),
                NEW_USER,
                new EmailAddress("hanako@example.com"),
                "新人 花子",
                "{bcrypt}$2a$10$unusable");
    }

    private static UserRegistrationRejection refusal(Company company, Set<Role> roles) {
        UserRegistrationDecision decision = User.decideRegistration(company, roles);
        assertThat(decision).isInstanceOf(UserRegistrationDecision.Refused.class);
        return ((UserRegistrationDecision.Refused) decision).rejection();
    }

    private static Company company(CompanyKind kind) {
        return Company.of(new CompanyId(UUID.randomUUID()), "企業 " + kind.name(), kind, true);
    }
}
