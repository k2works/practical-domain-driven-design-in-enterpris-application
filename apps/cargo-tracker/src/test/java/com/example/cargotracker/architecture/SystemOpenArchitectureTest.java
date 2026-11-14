package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * システムの有人案件の起票（{@code SystemServiceCaseOpener}。Bolt 40-18。W8 の週全体レビュー D）は認可を通らない内部の経路なので、画面（
 * {@code interfaces}）からは呼ばない。利用者の起票は認可を通す {@code ServiceCaseCommandService} を使う。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class SystemOpenArchitectureTest {

    @ArchTest
    static final ArchRule 画面はシステムの有人案件の起票を呼ばない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker..interfaces..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("SystemServiceCaseOpener");
}
