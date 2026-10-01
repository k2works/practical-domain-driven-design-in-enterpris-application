package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * AT-02: 依存は interfaces → application → domain ← infrastructure の向きに限る（バックエンドアーキテクチャ）。
 * サービスを組み立てる infrastructure.config（合成ルート）だけが、例外として application に依存してよい（D-5）。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule 層の依存は内側に向かう = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("interfaces")
            .definedBy("..interfaces..")
            .layer("application")
            .definedBy("..application..")
            .layer("domain")
            .definedBy("..domain..")
            .layer("infrastructure")
            .definedBy("..infrastructure..")
            .whereLayer("interfaces")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("application")
            .mayOnlyBeAccessedByLayers("interfaces", "infrastructure")
            .whereLayer("domain")
            .mayOnlyBeAccessedByLayers("interfaces", "application", "infrastructure")
            .whereLayer("infrastructure")
            .mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule 合成ルートの外のinfrastructureはapplicationに依存しない = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .and()
            .resideOutsideOfPackage("..infrastructure.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");
}
