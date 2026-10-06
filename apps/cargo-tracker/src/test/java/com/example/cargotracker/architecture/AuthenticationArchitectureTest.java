package com.example.cargotracker.architecture;

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
}
