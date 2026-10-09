package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * コンテキストの公開 API の形（バックエンドアーキテクチャ「公開 API の置き場所と形」。Bolt 17・25）。
 * 公開 API は照会・通知のインターフェースと引数・戻り値の record だけで、上流のドメインの型を漏らさない（イベントと同じ規則）。
 * 置き場所は開発ガイドライン第 3 章のインターフェース層の {@code <コンテキスト>.interfaces.api}（実装のインバウンドアダプターは
 * {@code interfaces.api.internal}。Bolt 25 の人の決定）。見積りの {@code quotation.api}（実装は application）は移すまでの例外として同じ規則で見る。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class PublicApiArchitectureTest {

    @ArchTest
    static final ArchRule 公開APIはJava標準と共有カーネルの型だけに依存する = classes()
            .that()
            .resideInAnyPackage("com.example.cargotracker.*.api..", "com.example.cargotracker.*.interfaces.api")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.example.cargotracker.shared.domain..",
                    "com.example.cargotracker.*.api..",
                    "com.example.cargotracker.*.interfaces.api");

    @ArchTest
    static final ArchRule 公開APIはインターフェースかrecordである = classes()
            .that()
            .resideInAnyPackage("com.example.cargotracker.*.api..", "com.example.cargotracker.*.interfaces.api")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beInterfaces()
            .orShould()
            .beRecords();
}
