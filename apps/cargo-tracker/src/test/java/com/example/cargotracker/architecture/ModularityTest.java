package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.CargoTrackerApplication;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * AT-03: コンテキスト間の依存は公開 API とドメインイベントだけに限る（ADR-001）。
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(CargoTrackerApplication.class);

    @Test
    void 見積りと経路設計とアクセス監査と予約と追跡と共有カーネルがモジュールとして認識される() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .contains("quotation", "routing", "identity", "booking", "tracking", "shared");
    }

    @Test
    void モジュール間の依存が境界を守っている() {
        modules.verify();
    }

    /** 予約は見積りの下流で、見積りは予約の型（イベントを含む）を参照しない（ADR-014。Bolt 23。Bolt 20 レビュー D-78 の一般化）。 */
    @Test
    void 見積りは予約に依存しない() {
        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.quotation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.example.cargotracker.booking..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.example.cargotracker"));
    }

    /** 予約は経路設計に依存しない。経路版は見積りが割り当てた経路の写しで確かめる（ADR-016。Bolt 23）。 */
    @Test
    void 予約は経路設計に依存しない() {
        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.booking..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.example.cargotracker.routing..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.example.cargotracker"));
    }

    /**
     * 下流から上流への通知は下流の listener が上流の公開 API を呼んで行い、上流は下流に依存しない（ADR-014。Bolt 20）。
     * 見積りは経路設計の型（イベントを含む）を参照しない。
     */
    @Test
    void 見積りは経路設計に依存しない() {
        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.quotation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.example.cargotracker.routing..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.example.cargotracker"));
    }

    /**
     * 追跡は予約・経路設計の下流で、予約と経路設計は追跡の型（イベントを含む）を参照しない。追跡の開始は追跡が DE-07 を購読して行い、
     * 結果を予約の公開 API で返す（ADR-015。Bolt 25）。{@code allowedDependencies} だけでは相手の宣言を書き換えれば通るため、型の依存でも確かめる。
     */
    @Test
    void 予約と経路設計は追跡に依存しない() {
        noClasses()
                .that()
                .resideInAnyPackage("com.example.cargotracker.booking..", "com.example.cargotracker.routing..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.example.cargotracker.tracking..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.example.cargotracker"));
    }
}
