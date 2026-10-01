package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * AT-01: ドメイン層はフレームワークにも、application・infrastructure・interfaces にも依存しない（第 3 章、ADR-001）。
 * 依存してよいのは Java の標準、ドメイン層（共有カーネルを含む）、設計上の役割を示す注釈の語彙だけ。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule ドメイン層はJava標準とドメイン層と注釈の語彙にだけ依存する = classes()
            .that().resideInAPackage("..domain..")
            .and().doNotHaveSimpleName("package-info")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "com.example.cargotracker..domain..",
                    "com.example.cargotracker.shared.annotation.ddd..");
}
