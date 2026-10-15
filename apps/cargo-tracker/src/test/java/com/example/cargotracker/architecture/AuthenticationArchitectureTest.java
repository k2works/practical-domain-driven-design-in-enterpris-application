package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

/**
 * identity 以外は認証の方式に依存しない（ADR-012、バックエンドアーキテクチャ「認証方式を業務規則へ埋め込まない」）。
 * 操作者と所属企業は共有カーネルの AuthenticatedActor だけで受け取る。TOTP の段を足しても業務のコンテキストは変わらない。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthenticationArchitectureTest {

    private static final String PASSWORD_VERIFIED_USER =
            "com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser";

    /** 認証の方式は identity に閉じる。コンテキストが増えても守りが外れないよう、identity 以外を対象にする（Bolt 14 レビュー）。 */
    @ArchTest
    static final ArchRule identity以外はSpringSecurityに依存しない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.security..");

    /**
     * TOTP のライブラリは暗号の部品（identity.infrastructure.crypto）に閉じる。ドメインは TotpCodeGenerator のポートだけを知る
     * （ADR-011、Bolt 30。Bolt 31d で crypto に狭めた）。
     */
    @ArchTest
    static final ArchRule TOTPのライブラリは暗号の部品だけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.eatthepath.otp..");

    /**
     * TOTP の秘密の平文を扱う JDK の暗号は暗号の部品（identity.infrastructure.crypto）に閉じる。ドメインとアプリケーションは暗号文
     * （EncryptedSecret）だけを受け渡す（Bolt 31、SEC-16、Bolt 30 の議題 2 の②。Bolt 31d で crypto に狭めた）。
     */
    @ArchTest
    static final ArchRule JDKの暗号は暗号の部品だけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("javax.crypto..");

    /**
     * 暗号化の部品（AesGcmSecretCipher）を参照してよいのは、暗号の部品と、組み立てる合成ルートと、dev の入力済みの部品だけ
     * （Bolt 31d）。認証の入口・画面・業務の層は暗号文の文字列とポートだけを扱う。
     */
    @ArchTest
    static final ArchRule 暗号化の部品は暗号の部品と合成ルートとdevの部品だけが参照する = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackages(
                    "com.example.cargotracker.identity.infrastructure.crypto..",
                    "com.example.cargotracker.identity.infrastructure.config..",
                    "com.example.cargotracker.identity.infrastructure.devlogin..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher");

    /**
     * 要素の権限（FACTOR_PASSWORD・FACTOR_TOTP）は identity.infrastructure に閉じる。ほかのコンテキストと identity の画面・業務の層は
     * 認証の段を知らない（Bolt 31、ADR-011 のコンプライアンス）。
     */
    @ArchTest
    static final ArchRule 要素の権限はidentityのinfrastructureだけが参照する = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.security.core.authority.FactorGrantedAuthority")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("TotpAuthenticationToken");

    /** 上の規則が空振りしていないことを確かめる。 */
    @ArchTest
    static final ArchRule 秘密の暗号化の部品はJDKの暗号を使う = classes()
            .that()
            .haveSimpleName("AesGcmSecretCipher")
            .should()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("javax.crypto..");

    /** QR のライブラリ（ZXing）は暗号の部品（identity.infrastructure.crypto）に閉じる（Bolt 31b、ADR-011。Bolt 31d で crypto に狭めた）。 */
    @ArchTest
    static final ArchRule QRのライブラリは暗号の部品だけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.google.zxing..");

    /** 上の規則が空振りしていないことを確かめる。 */
    @ArchTest
    static final ArchRule QRコードのSVGはQRのライブラリを使う = classes()
            .that()
            .haveSimpleName("QrCodeSvg")
            .should()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("com.google.zxing..");

    /**
     * 暗号の部品（identity.infrastructure.crypto）は認証の入口（identity.infrastructure.security）に依存しない（Bolt 31d、ADR-011 の
     * 決定 8）。層の規則 D-5 の認証の例外は入口だけにかかり、暗号の部品は application にも依存しない（合成ルートと入口の外の
     * infrastructure の規則。LayerArchitectureTest）。
     */
    @ArchTest
    static final ArchRule 暗号の部品は認証の入口に依存しない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.security..");

    /** 上の規則と D-5 の規則が空振りしていないこと（暗号の部品が crypto にあること）を確かめる。 */
    @ArchTest
    static final ArchRule 暗号の部品はcryptoにある = classes()
            .that()
            .haveSimpleName("AesGcmSecretCipher")
            .or()
            .haveSimpleName("Base32")
            .or()
            .haveSimpleName("BcryptPasswordHasher")
            .or()
            .haveSimpleName("BcryptRecoveryCodeHasher")
            .or()
            .haveSimpleName("BundledCommonPasswordList")
            .or()
            .haveSimpleName("JavaOtpTotpCodeGenerator")
            .or()
            .haveSimpleName("QrCodeSvg")
            .or()
            .haveSimpleName("RandomRecoveryCodeGenerator")
            .or()
            .haveSimpleName("RandomTotpSecretIssuer")
            .should()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.crypto..");

    /**
     * password の段だけの主体（{@code PasswordVerifiedUser}）を受ける画面は、A-02 と A-07 の controller だけ（Bolt 31c、ADR-012 の
     * 見直しの 4）。ほかの画面は 2 つの要素がそろった主体（{@code AuthenticatedActor}）だけを受ける。identity の外からの参照は
     * Spring Modulith の検証（ModularityTest）が止める。
     */
    @ArchTest
    static final ArchRule passwordの段の主体を受ける画面はA02とA07だけ = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..interfaces..")
            .and()
            .doNotHaveFullyQualifiedName("com.example.cargotracker.identity.interfaces.web.LoginController")
            .and()
            .doNotHaveFullyQualifiedName("com.example.cargotracker.identity.interfaces.web.TotpEnrollmentController")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(PASSWORD_VERIFIED_USER);

    /** 上の規則が空振りしていないことを確かめる。 */
    @ArchTest
    static final ArchRule A02とA07の画面はpasswordの段の主体を受ける = classes()
            .that()
            .haveFullyQualifiedName("com.example.cargotracker.identity.interfaces.web.LoginController")
            .or()
            .haveFullyQualifiedName("com.example.cargotracker.identity.interfaces.web.TotpEnrollmentController")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(PASSWORD_VERIFIED_USER);

    /** 上の規則が空振りしていないこと（パッケージ名の書き誤りで何も守らない状態にならないこと）を確かめる。 */
    @ArchTest
    static final ArchRule TOTPの生成器の実装はTOTPのライブラリを使う = classes()
            .that()
            .haveSimpleName("JavaOtpTotpCodeGenerator")
            .should()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.crypto..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("com.eatthepath.otp..");

    /**
     * パッケージの外で、identity の合成ルートが組み立ててよい型。設定の値から作る暗号の部品と、dev の A-02 の入力済みのコードの関数の
     * 型（合成ルートから画面の層への唯一の例外。Bolt 31）。
     */
    private static final Set<String> ALLOWED_BEAN_TYPES = Set.of(
            "com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher",
            "com.example.cargotracker.identity.interfaces.web.DevTotpCodeSource");

    /** identity の合成ルートが組み立ててよい型のパッケージ（そのパッケージとその下）。 */
    private static final List<String> ALLOWED_BEAN_PACKAGES = List.of(
            "com.example.cargotracker.identity.application.internal",
            "com.example.cargotracker.identity.infrastructure.security",
            "org.springframework");

    private static boolean isInPackage(JavaClass type, String prefix) {
        String name = type.getPackageName();
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    /** identity の合成ルートの {@code @Bean} が返してよい型（Bolt 32d、バックエンドアーキテクチャの identity の合成ルートの規則）。 */
    private static final ArchCondition<JavaMethod> 合成ルートが組み立ててよい型を返す =
            new ArchCondition<>("identity の合成ルートが組み立ててよい型を返す") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    JavaClass type = method.getRawReturnType();
                    boolean allowed = ALLOWED_BEAN_PACKAGES.stream().anyMatch(prefix -> isInPackage(type, prefix))
                            || ALLOWED_BEAN_TYPES.contains(type.getName());
                    events.add(new SimpleConditionEvent(
                            method, allowed, method.getFullName() + " は " + type.getName() + " を返す"));
                }
            };

    /**
     * identity の送信アダプター（persistence・crypto）は部品探索で拾い、合成ルートの {@code @Bean} はアプリケーションのサービス、
     * Spring Security の入口と部品、設定の値から作る {@code AesGcmSecretCipher}、dev の {@code DevTotpCodeSource} に限る（Bolt 32d）。{@code @Bean} がないと空振りする
     * ので、なければ落とす（T-107）。
     */
    @ArchTest
    static final ArchRule identityの合成ルートのBeanは規則の型だけを返す = methods()
            .that()
            .areAnnotatedWith("org.springframework.context.annotation.Bean")
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.config..")
            .should(合成ルートが組み立ててよい型を返す)
            .allowEmptyShould(false);

    /**
     * identity の合成ルートは送信アダプター（persistence・crypto）を自分で作らない（部品探索で拾う。Bolt 32d の開発レビュー）。
     * {@code @Bean} の戻り値の型の見張りだけでは、サービスの {@code @Bean} の本体で {@code new} する書き方を見逃すため。設定の値から
     * 作る {@code AesGcmSecretCipher} は工場のメソッド（{@code fromBase64Keys}）で作るので、この規則に当たらない。
     */
    @ArchTest
    static final ArchRule identityの合成ルートは送信アダプターを作らない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure.config..")
            .should()
            .callConstructorWhere(com.tngtech.archunit.core.domain.JavaConstructorCall.Predicates.target(
                    com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage(
                                    "com.example.cargotracker.identity.infrastructure.persistence..",
                                    "com.example.cargotracker.identity.infrastructure.crypto.."))));
}
