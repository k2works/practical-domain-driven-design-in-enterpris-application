package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

    /** TOTP のライブラリは identity.infrastructure に閉じる。ドメインは TotpCodeGenerator のポートだけを知る（ADR-011、Bolt 30）。 */
    @ArchTest
    static final ArchRule TOTPのライブラリはidentityのinfrastructureだけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.eatthepath.otp..");

    /**
     * TOTP の秘密の平文を扱う暗号化は identity.infrastructure に閉じる。ドメインとアプリケーションは暗号文（EncryptedSecret）
     * だけを受け渡す（Bolt 31、SEC-16、Bolt 30 の議題 2 の②）。
     */
    @ArchTest
    static final ArchRule 秘密の暗号化はidentityのinfrastructureだけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("javax.crypto..")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("AesGcmSecretCipher");

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
            .resideInAPackage("com.example.cargotracker.identity.infrastructure..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("javax.crypto..");

    /** QR のライブラリ（ZXing）は identity.infrastructure に閉じる（Bolt 31b、ADR-011）。 */
    @ArchTest
    static final ArchRule QRのライブラリはidentityのinfrastructureだけが使う = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..")
            .and()
            .resideOutsideOfPackage("com.example.cargotracker.identity.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.google.zxing..");

    /** 上の規則が空振りしていないことを確かめる。 */
    @ArchTest
    static final ArchRule 秘密の発行の実装はQRのライブラリを使う = classes()
            .that()
            .haveSimpleName("RandomTotpSecretIssuer")
            .should()
            .resideInAPackage("com.example.cargotracker.identity.infrastructure..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("com.google.zxing..");

    /**
     * 秘密の発行と回復コードの生成の実装は、認証の入口ではないので application に依存しない（Bolt 31b）。層の規則 D-5 の認証の例外は
     * {@code identity.infrastructure.security} 全体にかかるので、入口でない部品の守りを足す（入口と暗号のパッケージは 31d で諮る）。
     */
    @ArchTest
    static final ArchRule 秘密の発行と回復コードの生成の実装はapplicationに依存しない = noClasses()
            .that()
            .haveSimpleName("RandomTotpSecretIssuer")
            .or()
            .haveSimpleName("RandomRecoveryCodeGenerator")
            .or()
            .haveSimpleName("AesGcmSecretCipher")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

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
            .resideInAPackage("com.example.cargotracker.identity.infrastructure..")
            .andShould()
            .dependOnClassesThat()
            .resideInAPackage("com.eatthepath.otp..");
}
