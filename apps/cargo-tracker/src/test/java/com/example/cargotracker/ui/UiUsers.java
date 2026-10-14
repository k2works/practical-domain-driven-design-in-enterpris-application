package com.example.cargotracker.ui;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.crypto.Base32;
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

    /** テストの利用者の回復コードの 1 つ（本物の秘密ではない）。 */
    private static final String RECOVERY_CODE = "7e57-c0de";

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TotpCredentialRepository totpCredentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final RecoveryCodeHasher recoveryCodeHasher;
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
            RecoveryCodeRepository recoveryCodes,
            RecoveryCodeHasher recoveryCodeHasher,
            TotpCodeGenerator totpCodes,
            AesGcmSecretCipher cipher,
            Clock clock,
            JdbcTemplate jdbc) {
        this.companies = companies;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.totpCredentials = totpCredentials;
        this.recoveryCodes = recoveryCodes;
        this.recoveryCodeHasher = recoveryCodeHasher;
        this.totpCodes = totpCodes;
        this.cipher = cipher;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /** 前後 1 つの刻みのどのコードとも一致しないコード（誤ったコードの送信のため）。 */
    public String wrongTotpCode(Role role) {
        TotpCredential credential =
                totpCredentials.findByUserId(of(role).userId()).orElseThrow();
        long step = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        java.util.List<String> window = java.util.stream.LongStream.rangeClosed(step - 1, step + 1)
                .mapToObj(each -> totpCodes.code(credential.userId(), credential.secret(), each))
                .toList();
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> String.format("%06d", i))
                .filter(code -> !window.contains(code))
                .findFirst()
                .orElseThrow();
    }

    /**
     * A-02 に入れる、その時点のコード。同じ利用者が同じ刻みの間に入り直しても再利用として拒まれないよう、テストの準備として
     * 使った刻みを戻してから作る（本体の再利用の拒否は変えない。Bolt 31 の確認ポイント 7）。
     */
    public String totpCode(Role role) {
        UserId userId = of(role).userId();
        jdbc.update("UPDATE identity.totp_credential SET last_used_step = 0 WHERE user_id = ?", userId.value());
        TotpCredential credential = totpCredentials.findByUserId(userId).orElseThrow();
        return totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    /**
     * 役割の利用者に回復コード 10 個を発行し、そのうち 1 つの平文を返す（A-02 の回復コードのシナリオ。Bolt 31c）。平文はテストの
     * 準備で決めた値で、本物の秘密ではない。
     */
    public String recoveryCode(Role role) {
        UserId userId = of(role).userId();
        UtcInstant now = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                userId,
                java.util.stream.IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(
                                userId,
                                i == 0 ? RECOVERY_CODE : String.format("0000-%04x", i),
                                recoveryCodeHasher,
                                now))
                        .toList());
        return RECOVERY_CODE;
    }

    /**
     * 役割の利用者の利用を止める（AC6 のシナリオ。Bolt 32c）。利用停止の操作（US-16）は Bolt 34 で作るので、テストの準備として
     * DB の値を変える。
     */
    public void suspend(Role role) {
        jdbc.update(
                "UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?",
                of(role).userId().value());
    }

    /** 役割の利用者のメールアドレスと password。初めて求められたときに作る。 */
    public Credentials of(Role role) {
        return created.computeIfAbsent(role, each -> create(each, true));
    }

    /** TOTP を登録していない役割の利用者（A-07 の登録のシナリオ。Bolt 31b）。初めて求められたときに作る。 */
    public Credentials ofWithoutTotp(Role role) {
        return created.computeIfAbsent(role, each -> create(each, false));
    }

    /** A-07 の手入力のキー（Base32）から、利用者の認証アプリと同じようにその時点のコードを作る（Bolt 31b）。 */
    public String codeFromKey(Role role, String manualKey) {
        UserId owner = of(role).userId();
        return totpCodes.code(
                owner,
                cipher.encrypt(Base32.decode(manualKey.replace(" ", "")), owner),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private Credentials create(Role role, boolean enrollTotp) {
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
        if (enrollTotp) {
            totpCredentials.add(TotpCredential.enroll(
                    userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        }
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
