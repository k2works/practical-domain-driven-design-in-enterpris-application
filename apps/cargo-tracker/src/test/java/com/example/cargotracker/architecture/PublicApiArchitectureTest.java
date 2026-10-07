package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * コンテキストの公開 API（`<コンテキスト>.api`）の形（バックエンドアーキテクチャ「公開 API の置き場所と形」。Bolt 17）。
 * 公開 API は照会のインターフェースと戻り値の record だけで、上流のドメインの型を漏らさない（イベントと同じ規則）。
 * 実装は上流の application に置く。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class PublicApiArchitectureTest {

    @ArchTest
    static final ArchRule 公開APIはJava標準と共有カーネルの型だけに依存する = classes()
            .that()
            .resideInAPackage("com.example.cargotracker.*.api..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..", "com.example.cargotracker.shared.domain..", "com.example.cargotracker.*.api..");

    @ArchTest
    static final ArchRule 公開APIはインターフェースかrecordである = classes()
            .that()
            .resideInAPackage("com.example.cargotracker.*.api..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beInterfaces()
            .orShould()
            .beRecords();
}
