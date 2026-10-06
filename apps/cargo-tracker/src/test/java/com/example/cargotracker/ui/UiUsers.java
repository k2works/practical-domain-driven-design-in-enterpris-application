package com.example.cargotracker.ui;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.spring.ScenarioScope;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 画面の層のシナリオで使う利用者（Bolt 14）。シナリオごとに荷主の企業と荷主担当者、A 社の営業担当者を作り、
 * ほかのシナリオと共有しない（テストのデータを分ける。ADR-011 の「テストごとのデータ」）。
 */
@ScenarioScope
public class UiUsers {

    /** テストの利用者の password（本物の秘密ではない）。 */
    static final String PASSWORD = "ui-test-password";

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Map<Role, Credentials> created = new EnumMap<>(Role.class);
    private final String suffix = UUID.randomUUID().toString();

    public UiUsers(CompanyRepository companies, UserRepository users, PasswordEncoder passwordEncoder) {
        this.companies = companies;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** 役割の利用者のメールアドレスと password。初めて求められたときに作る。 */
    public Credentials of(Role role) {
        return created.computeIfAbsent(role, this::create);
    }

    private Credentials create(Role role) {
        boolean shipper = role == Role.SHIPPER;
        Company company = Company.of(
                new CompanyId(UUID.randomUUID()),
                shipper ? "荷主 テスト" : "A 社",
                shipper ? CompanyKind.SHIPPER : CompanyKind.OPERATOR,
                true);
        companies.add(company);
        String email = role.name().toLowerCase(java.util.Locale.ROOT) + "-" + suffix + "@ui.example";
        users.add(User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                shipper ? "荷主 太郎" : "営業 一郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        return new Credentials(email, PASSWORD);
    }

    /** ログインに使う値。 */
    public record Credentials(String email, String password) {}
}
