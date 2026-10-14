package com.example.cargotracker.identity;

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
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 認証の要素のテストで、外部キーのための企業と利用者を作る（並行のテストと共有する。Bolt 30、Bolt 31 で抜き出した）。 */
public final class AuthenticationFactorFixtures {

    private AuthenticationFactorFixtures() {}

    public static UserId newUser(CompanyRepository companies, UserRepository users) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                "荷主 太郎",
                "{noop}x",
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));
        return userId;
    }

    /**
     * テストの準備として、利用者の TOTP の使った刻みを 0 に戻す（同じ利用者が同じ刻みの間にログインし直しても、再利用として拒まれない
     * ように。本体の再利用の拒否は変えない。Bolt 31 の確認ポイント 7。Bolt 32d で 3 か所の SQL をここにまとめた）。
     */
    public static void resetTotpStep(JdbcTemplate jdbc, UserId userId) {
        jdbc.update("UPDATE identity.totp_credential SET last_used_step = 0 WHERE user_id = ?", userId.value());
    }

    /** メールアドレスの利用者の TOTP の使った刻みを 0 に戻す（{@link #resetTotpStep(JdbcTemplate, UserId)} と同じ）。 */
    public static void resetTotpStep(JdbcTemplate jdbc, String email) {
        jdbc.update(
                "UPDATE identity.totp_credential SET last_used_step = 0 WHERE user_id ="
                        + " (SELECT id FROM identity.app_user WHERE email = ?)",
                email);
    }
}
