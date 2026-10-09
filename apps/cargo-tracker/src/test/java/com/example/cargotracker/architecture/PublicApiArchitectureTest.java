package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * コンテキストの公開 API の形（バックエンドアーキテクチャ「公開 API の置き場所と形」。Bolt 17・25）。
 * 公開 API は照会・通知のインターフェースと引数・戻り値の record だけで、上流のドメインの型を漏らさない（イベントと同じ規則）。
 * 置き場所は開発ガイドライン第 3 章のインターフェース層の {@code <コンテキスト>.interfaces.api}（実装のインバウンドアダプターは
 * {@code interfaces.api.internal}。Bolt 25 の人の決定）。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class PublicApiArchitectureTest {

    @ArchTest
    static final ArchRule 公開APIはJava標準と共有カーネルの型だけに依存する = classes()
            .that()
            .resideInAPackage("com.example.cargotracker.*.interfaces.api")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..", "com.example.cargotracker.shared.domain..", "com.example.cargotracker.*.interfaces.api");

    @ArchTest
    static final ArchRule 公開APIはインターフェースかrecordである = classes()
            .that()
            .resideInAPackage("com.example.cargotracker.*.interfaces.api")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beInterfaces()
            .orShould()
            .beRecords();

    /**
     * 公開 API は {@code <コンテキスト>.interfaces.api} にだけ置き、ほかの置き場所（たとえばコンテキストの直下の {@code api}）を作らない
     * （開発ガイドライン第 3 章のインターフェース層。2026-10-09 の人の指示で、見積りの {@code quotation.api} も移した）。
     */
    @ArchTest
    static final ArchRule 公開APIはinterfacesのapiにだけ置く = noClasses()
            .should()
            .resideInAPackage("com.example.cargotracker.*.api..")
            .because("公開 API はインターフェース層の <コンテキスト>.interfaces.api に置く（開発ガイドライン第 3 章）");
}
