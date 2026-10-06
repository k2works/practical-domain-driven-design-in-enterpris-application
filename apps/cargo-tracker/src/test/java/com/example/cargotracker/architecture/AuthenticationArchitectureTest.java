package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 業務のコンテキストは認証の方式に依存しない（ADR-012、バックエンドアーキテクチャ「認証方式を業務規則へ埋め込まない」）。
 * 操作者と所属企業は共有カーネルの AuthenticatedActor だけで受け取る。TOTP の段を足しても業務のコンテキストは変わらない。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthenticationArchitectureTest {

    @ArchTest
    static final ArchRule 見積りのコンテキストはSpringSecurityに依存しない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker.quotation..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.security..");

    @ArchTest
    static final ArchRule 見積りのコンテキストは仮の主体を使わない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker.quotation..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("ProvisionalActorProperties");
}
