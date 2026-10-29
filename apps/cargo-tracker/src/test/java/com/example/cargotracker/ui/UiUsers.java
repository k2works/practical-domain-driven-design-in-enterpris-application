package com.example.cargotracker.ui;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
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
    /** シナリオで作った企業（システム管理者の管理範囲に割り当てる。Bolt 34b）。 */
    private final java.util.List<CompanyId> scenarioCompanies = new java.util.ArrayList<>();

    private Credentials outsider;
    /** 営業担当者の担当企業の外の荷主の企業（Bolt 38f）。 */
    private CompanyId outOfScopeShipper;
    /** ログインの前に未使用にしておく回復コードの数（残りの回復コードの案内のシナリオ。Bolt 38d2）。既定は発行した 10 個のまま。 */
    private int unusedRecoveryCodesBeforeLogin = RecoveryCode.CODES_PER_USER;

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
        return totpCode(of(role).userId());
    }

    /** {@link #totpCode(Role)} と同じで、画面で登録した利用者のための口（Bolt 38b4）。 */
    public String totpCode(UserId userId) {
        AuthenticationFactorFixtures.resetTotpStep(jdbc, userId);
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
        java.util.List<RecoveryCode> issued = java.util.stream.IntStream.range(0, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(
                        userId, i == 0 ? RECOVERY_CODE : String.format("0000-%04x", i), recoveryCodeHasher, now))
                .toList();
        recoveryCodes.replaceAll(userId, issued);
        // 返す平文（先頭）は残し、後ろから使用済みにする（Bolt 38d2）
        for (int i = RecoveryCode.CODES_PER_USER - 1; i >= unusedRecoveryCodesBeforeLogin; i--) {
            recoveryCodes.use(userId, issued.get(i).id(), now);
        }
        return RECOVERY_CODE;
    }

    /**
     * ログインの前に未使用にしておく回復コードの数を決める（残りの回復コードの案内のシナリオ。Bolt 38d2）。数は返す平文を含み、
     * {@link #recoveryCode(Role)} が発行した後に残りを使用済みにする（10 個そろいでしか発行できないため）。
     */
    public void leaveUnusedRecoveryCodesBeforeLogin(int count) {
        if (count < 1 || count > RecoveryCode.CODES_PER_USER) {
            throw new IllegalArgumentException("ログインに使う 1 個を含めて 1〜10 個: " + count);
        }
        unusedRecoveryCodesBeforeLogin = count;
    }

    /**
     * 役割の利用者の利用を止める（AC6 のシナリオ。Bolt 32c）。テストの準備として DB の値を変える（画面の利用停止の操作は Bolt 34d の
     * {@code manage_user_status_ui.feature} で確かめる）。
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

    /**
     * システム管理者の管理範囲の外の企業の利用者（Bolt 34b の AC2 のシナリオ）。カスタマーサポートの役割で、シナリオのシステム管理者に
     * 企業を割り当てない。初めて求められたときに作る。
     */
    public Credentials outsideAdministration() {
        if (outsider == null) {
            outsider = create(Role.CUSTOMER_SUPPORT, true, false);
        }
        return outsider;
    }

    /** A-07 の手入力のキー（Base32）から、利用者の認証アプリと同じようにその時点のコードを作る（Bolt 31b）。 */
    public String codeFromKey(Role role, String manualKey) {
        return codeFromKey(of(role).userId(), manualKey);
    }

    /** {@link #codeFromKey(Role, String)} と同じで、画面で登録した利用者のための口（Bolt 38b4）。 */
    public String codeFromKey(UserId owner, String manualKey) {
        return totpCodes.code(
                owner,
                cipher.encrypt(Base32.decode(manualKey.replace(" ", "")), owner),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private Credentials create(Role role, boolean enrollTotp) {
        return create(role, enrollTotp, true);
    }

    /**
     * 利用者を作る。シナリオのシステム管理者は、シナリオで作った企業（自分の企業を含む）を管理範囲に持つ（Bolt 34b。割り当ては
     * 運用の手順で入れるので、テストの準備として表に入れる）。{@code administered} が偽の利用者の企業は割り当てない。
     */
    private Credentials create(Role role, boolean enrollTotp, boolean administered) {
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
        if (administered) {
            // システム管理者を作る間は created にまだないので、今いるシステム管理者（いれば）にだけ入る
            addScenarioCompany(company.id());
            if (role == Role.SYSTEM_ADMIN) {
                scenarioCompanies.forEach(each -> AdministrationFixtures.assignCompany(jdbc, userId, each));
            }
        }
        return new Credentials(userId, email, PASSWORD);
    }

    /**
     * 名前を決めた運用する会社を作り、シナリオの企業としてシステム管理者の管理範囲に入れる（Bolt 38b3b。利用者の登録で、利用者ごとに
     * 作る「A 社」と名前が重ならない企業を選ぶため）。
     */
    public CompanyId administeredOperator(String name) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), name, CompanyKind.OPERATOR, true);
        companies.add(company);
        addScenarioCompany(company.id());
        return company.id();
    }

    /**
     * 名前を決めた荷主の企業を作り、シナリオの企業としてシステム管理者の管理範囲に入れる（Bolt 38e。担当範囲の領域で担当企業として選ぶ
     * 企業。荷主担当者の利用者は作らない）。
     */
    public CompanyId administeredShipper(String name) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), name, CompanyKind.SHIPPER, true);
        companies.add(company);
        addScenarioCompany(company.id());
        return company.id();
    }

    /**
     * シナリオの企業に足し、作ってあればシナリオのシステム管理者の管理範囲に入れる（Bolt 38e の開発レビューの中-4。作る順によらず
     * 「シナリオの管理者がシナリオの企業を管理する」。後から作るシステム管理者は {@link #create} が全部の企業を入れる）。
     */
    private void addScenarioCompany(CompanyId company) {
        scenarioCompanies.add(company);
        if (created.containsKey(Role.SYSTEM_ADMIN)) {
            AdministrationFixtures.assignCompany(
                    jdbc, created.get(Role.SYSTEM_ADMIN).userId(), company);
        }
    }

    /**
     * 営業担当者に、シナリオの荷主担当者の企業を担当させる（Bolt 38f。社内の画面は担当企業の見積依頼だけを示す）。割り当ては S-20 で
     * 入れるので、テストの準備として表に入れる。利用者を作るときには割り当てない（明示の前提で入れる。38e の #50 (a)）。
     */
    public void assignShipperCompanyToSales() {
        assignShipperCompanyTo(Role.SALES);
    }

    /**
     * 業務担当者の役割の利用者に、シナリオの荷主担当者の企業を担当させる（Bolt 38g。経路設計者・追跡管理者も担当企業の案件と追跡記録だけを
     * 扱う）。割り当ては明示の前提で入れ、利用者を作るときには割り当てない（38e の #50 (a)）。
     */
    public void assignShipperCompanyTo(Role role) {
        AdministrationFixtures.assignCompany(jdbc, of(role).userId(), companyOf(Role.SHIPPER));
    }

    /** 役割の利用者の企業の名前（画面に出る企業名を確かめるため。Bolt 38g）。 */
    public String companyNameOf(Role role) {
        return companies.findById(companyOf(role)).orElseThrow().name();
    }

    /** 役割の利用者の企業。 */
    public CompanyId companyOf(Role role) {
        return users.findById(of(role).userId()).orElseThrow().companyId();
    }

    /**
     * 営業担当者の担当企業の外の荷主の企業（Bolt 38f。範囲の外の見積依頼を置く）。荷主担当者の利用者は作らず、名前は担当企業と見分ける。
     * 初めて求められたときに作る。
     */
    public CompanyId outOfScopeShipper() {
        if (outOfScopeShipper == null) {
            Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 範囲外", CompanyKind.SHIPPER, true);
            companies.add(company);
            outOfScopeShipper = company.id();
        }
        return outOfScopeShipper;
    }

    /** 役割の利用者の表示名（画面に出る名前を確かめるため）。 */
    public String displayNameOf(Role role) {
        return displayName(role);
    }

    private static String displayName(Role role) {
        return switch (role) {
            case SHIPPER -> "荷主 太郎";
            case ROUTE_DESIGNER -> "経路 設計";
            case TRACKING_MANAGER -> "追跡 管理";
            case SALES -> "営業 一郎";
            case SYSTEM_ADMIN -> "管理 次郎";
            case AUDITOR -> "監査 花子";
            case CUSTOMER_SUPPORT -> "窓口 三郎";
            default -> throw new IllegalArgumentException("画面の層のテストの利用者を用意していない役割です: " + role);
        };
    }

    /** ログインに使う値。 */
    public record Credentials(UserId userId, String email, String password) {}
}
