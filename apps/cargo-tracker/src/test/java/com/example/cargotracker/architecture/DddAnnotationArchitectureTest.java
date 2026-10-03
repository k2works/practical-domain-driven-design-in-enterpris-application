package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 開発ガイド（第 1 章）の概念の注釈を、その役割を持つパッケージのクラスに付ける（shared.annotation.ddd）。
 * パッケージで役割が決まるドメインルールとコマンドは、付け忘れをここで見つける。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class DddAnnotationArchitectureTest {

    private static final String DDD = "com.example.cargotracker.shared.annotation.ddd.";

    @ArchTest
    static final ArchRule ドメインルールにはDomainRuleを付ける = classes()
            .that()
            .resideInAPackage("..domain.model.rules..")
            .and()
            .arePublic()
            .and()
            .areTopLevelClasses()
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beAnnotatedWith(DDD + "DomainRule");

    @ArchTest
    static final ArchRule コマンドにはCommandを付ける = classes()
            .that()
            .resideInAPackage("..application.internal.commands..")
            .and()
            .arePublic()
            .and()
            .areTopLevelClasses()
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beAnnotatedWith(DDD + "Command");
}
