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
