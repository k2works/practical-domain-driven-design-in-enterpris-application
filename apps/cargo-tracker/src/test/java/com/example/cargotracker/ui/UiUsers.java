package com.example.cargotracker.ui;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.spring.ScenarioScope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 画面の層のシナリオで使う利用者（Bolt 14）。シナリオごとに荷主の企業と荷主担当者、A 社の営業担当者・経路設計者（Bolt 17）を作り、
 * ほかのシナリオと共有しない（テストのデータを分ける。ADR-011 の「テストごとのデータ」）。
 * 利用者はみな TOTP を登録し、A-02 にはテストの固定の秘密からその時点のコードを入れる（Bolt 31）。
 */
@ScenarioScope
public class UiUsers {

    /** テストの利用者の password（本物の秘密ではない）。 */
    static final String PASSWORD = "ui-test-password";

    /** テストの利用者の TOTP の秘密（本物の秘密ではない）。 */
    private static final byte[] TOTP_SECRET = "ui-test-totp-secret!".getBytes(StandardCharsets.US_ASCII);

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TotpCredentialRepository totpCredentials;
    private final TotpCodeGenerator totpCodes;
    private final AesGcmSecretCipher cipher;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final Map<Role, Credentials> created = new EnumMap<>(Role.class);
    private final String suffix = UUID.randomUUID().toString();

    @SuppressWarnings("java:S107") // 利用者と TOTP の資格を作る部品をそろえて受ける（シナリオの範囲の部品）
    public UiUsers(
            CompanyRepository companies,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TotpCredentialRepository totpCredentials,
            TotpCodeGenerator totpCodes,
            AesGcmSecretCipher cipher,
            Clock clock,
            JdbcTemplate jdbc) {
        this.companies = companies;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.totpCredentials = totpCredentials;
        this.totpCodes = totpCodes;
        this.cipher = cipher;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /**
     * A-02 に入れる、その時点のコード。同じ利用者が同じ刻みの間に入り直しても再利用として拒まれないよう、テストの準備として
     * 使った刻みを戻してから作る（本体の再利用の拒否は変えない。Bolt 31 の確認ポイント 7）。
     */
    public String totpCode(Role role) {
        UserId userId = of(role).userId();
        jdbc.update("UPDATE identity.totp_credential SET last_used_step = 0 WHERE user_id = ?", userId.value());
        TotpCredential credential = totpCredentials.findByUserId(userId).orElseThrow();
        return totpCodes.code(credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
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
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(email),
                displayName(role),
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        totpCredentials.add(
                TotpCredential.enroll(userId, cipher.encrypt(TOTP_SECRET.clone()), new UtcInstant(clock.instant())));
        return new Credentials(userId, email, PASSWORD);
    }

    private static String displayName(Role role) {
        return switch (role) {
            case SHIPPER -> "荷主 太郎";
            case ROUTE_DESIGNER -> "経路 設計";
            case TRACKING_MANAGER -> "追跡 管理";
            case SALES -> "営業 一郎";
            default -> throw new IllegalArgumentException("画面の層のテストの利用者を用意していない役割です: " + role);
        };
    }

    /** ログインに使う値。 */
    public record Credentials(UserId userId, String email, String password) {}
}
