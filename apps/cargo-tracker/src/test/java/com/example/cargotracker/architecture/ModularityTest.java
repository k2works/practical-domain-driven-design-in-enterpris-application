package com.example.cargotracker.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.CargoTrackerApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * AT-03: コンテキスト間の依存は公開 API とドメインイベントだけに限る（ADR-001）。
 */
class ModularityTest {

    /** 本番のクラス（テストを含めない）。規則ごとに読み込み直さない。 */
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.cargotracker");

    /** 外部データのインフラストラクチャ層に 1 クラスだけ許すイベントの発行（Bolt 39b3）。 */
    private static final String EVENT_PUBLISHER = "org.springframework.context.ApplicationEventPublisher";

    private final ApplicationModules modules = ApplicationModules.of(CargoTrackerApplication.class);

    @Test
    void 見積りと経路設計とアクセス監査と予約と追跡と通知と外部データと共有カーネルがモジュールとして認識される() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .contains(
                        "quotation",
                        "routing",
                        "identity",
                        "booking",
                        "tracking",
                        "notification",
                        "externaldata",
                        "shared");
    }

    @Test
    void モジュール間の依存が境界を守っている() {
        modules.verify();
    }

    /** 外部データの DE-12〜DE-14 は名前付きインターフェース events で公開し、下流は externaldata :: events だけを参照する（Bolt 39b3 の行 16）。 */
    @Test
    void 行16_外部データのイベントは名前付きインターフェースeventsとして認識される() {
        assertThat(modules.getModuleByName("externaldata")
                        .orElseThrow()
                        .getNamedInterfaces()
                        .getByName("events"))
                .isPresent();
    }

    /**
     * アクセス・監査は外部原本の採否を監査するため外部データの公表された言語（{@code externaldata :: events}）を宣言し、外部データの
     * イベントのほかは参照しない（Bolt 39b3b の行 11、IA-INV-15）。外部データがアクセス・監査を含むほかのコンテキストに依存しないことは
     * 外部データの許可リストの規則が守る。
     */
    @Test
    void 行11_アクセス監査は外部データのイベントだけに依存を宣言して参照する() throws ClassNotFoundException {
        ApplicationModule identity =
                Class.forName("com.example.cargotracker.identity.package-info").getAnnotation(ApplicationModule.class);
        assertThat(identity.allowedDependencies()).contains("externaldata :: events");

        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.identity..")
                .should()
                .dependOnClassesThat(resideInAPackage("com.example.cargotracker.externaldata..")
                        .and(resideOutsideOfPackage("com.example.cargotracker.externaldata.domain.events..")))
                .check(PRODUCTION_CLASSES);
    }

    /** 経路設計は外部データの公表された言語（{@code externaldata :: events}）を宣言し、外部データのイベントのほかは参照しない（Bolt 39e の行 36、ADR-004）。 */
    @Test
    void 行36_経路設計は外部データのイベントだけに依存を宣言して参照する() throws ClassNotFoundException {
        ApplicationModule routing =
                Class.forName("com.example.cargotracker.routing.package-info").getAnnotation(ApplicationModule.class);
        assertThat(routing.allowedDependencies()).contains("externaldata :: events");

        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.routing..")
                .should()
                .dependOnClassesThat(resideInAPackage("com.example.cargotracker.externaldata..")
                        .and(resideOutsideOfPackage("com.example.cargotracker.externaldata.domain.events..")))
                .check(PRODUCTION_CLASSES);
    }

    /** 追跡は外部データの公表された言語（{@code externaldata :: events}）を宣言し、外部データのイベントのほかは参照しない（Bolt 39g の行 47、ADR-004）。 */
    @Test
    void Bolt39g_行47_追跡は外部データのイベントだけに依存を宣言して参照する() throws ClassNotFoundException {
        ApplicationModule tracking =
                Class.forName("com.example.cargotracker.tracking.package-info").getAnnotation(ApplicationModule.class);
        assertThat(tracking.allowedDependencies()).contains("externaldata :: events");

        noClasses()
                .that()
                .resideInAPackage("com.example.cargotracker.tracking..")
                .should()
                .dependOnClassesThat(resideInAPackage("com.example.cargotracker.externaldata..")
                        .and(resideOutsideOfPackage("com.example.cargotracker.externaldata.domain.events..")))
                .check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
    }

    /**
     * 外部データはほかのコンテキストに依存しない上流で、依存してよい相手を許可リストで並べる（ADR-004 の改訂。Bolt 39）。
     * 禁止する相手を名前で並べる形では、新しいコンテキストを足すたびに規則を書き足す必要があるため、許してよい相手だけを書く。
     * 許可リストは層別にする（Bolt 39a2）。この規則はドメイン層とファイルの読み取りの層（{@code interfaces.file}）を見るが、domain は
     * DomainArchitectureTest が先に守るので、実質この規則だけが守るのは {@code interfaces.file}。
     * フレームワークの許可は、使う Bolt で、使う層に限って足す（W10 の AWS SDK もインフラストラクチャ層に足す）。
     * アプリケーション層は次の規則で、画面の層（{@code interfaces.web}）はその次の規則で別に見る（Bolt 39c・39d3b2）。
     */
    @Test
    void 外部データのドメイン層とファイルの読み取りの層は共有カーネルとJavaとSpringにだけ依存する() {
        classes()
                .that()
                .resideInAPackage("com.example.cargotracker.externaldata..")
                .and()
                .resideOutsideOfPackages(
                        "com.example.cargotracker.externaldata.infrastructure..",
                        "com.example.cargotracker.externaldata.interfaces.web..",
                        "com.example.cargotracker.externaldata.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "com.example.cargotracker.externaldata..",
                        "com.example.cargotracker.shared..",
                        "org.springframework..")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * 外部データのアプリケーション層は、上の許可リストに加えて {@code org.slf4j} に依存してよい。Bolt 39d3c の【前の決定の変更】: application 層の照会のサービスの
     * WARN のログのため {@code org.slf4j} を許す（2026-11-05、推奨のとおり。Recommend 承認の指示による。human:kakimomokuri）。
     * Bolt 39d3b2 の【前の決定の変更】(1): その許可を application 層に限り、{@code interfaces.file} には許さない（2026-11-09、推奨のとおり。Recommend 承認の指示に
     * よる。human:kakimomokuri）。domain は DomainArchitectureTest が Java の標準とドメイン層と注釈の語彙だけに絞るので、domain にロガーは入らない。
     */
    @Test
    void 外部データのアプリケーション層は共有カーネルとJavaとSpringとSLF4Jにだけ依存する() {
        classes()
                .that()
                .resideInAPackage("com.example.cargotracker.externaldata.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "com.example.cargotracker.externaldata..",
                        "com.example.cargotracker.shared..",
                        "org.springframework..",
                        "org.slf4j..")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * 外部データの画面の層（{@code interfaces.web}。S-16）は、上の許可リストに加えて画面の表示の部品（{@code platform.web}。日時表示）に
     * 依存してよい（Bolt 39c の【前の決定の変更】。2026-11-04、推奨のとおり。Recommend 承認の指示による。human:kakimomokuri）。
     * {@code platform.web} を参照できるのは画面の層だけという全体の規則（LayerArchitectureTest）はそのまま守る。
     */
    @Test
    void 外部データの画面の層は共有カーネルと画面の基盤とJavaとSpringにだけ依存する() {
        classes()
                .that()
                .resideInAPackage("com.example.cargotracker.externaldata.interfaces.web..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "com.example.cargotracker.externaldata..",
                        "com.example.cargotracker.shared..",
                        "com.example.cargotracker.platform.web..",
                        "org.springframework..")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * 外部データのインフラストラクチャ層は MyBatis と、Spring のうち実際に使う範囲（例外の変換・トランザクション・ステレオタイプ）に
     * 依存してよい（外部原本の保存の DB の実装。Bolt 39a2）。フレームワークの許可は、使う Bolt で、使う層に限って足す。
     * 組み立てのクラス（{@code infrastructure.config} の {@code @Configuration}・{@code @Bean}）のため
     * {@code org.springframework.context.annotation} を足した（Bolt 39b の確認ポイント 14。2026-11-03 の人の決定）。
     * 取込のサービスにイベントの発行（DE-12〜DE-14）を渡す組み立てのため、{@code ApplicationEventPublisher} の 1 クラスだけを
     * クラス名の完全一致で足した（{@code org.springframework.context} のパッケージ全体は許さない。Bolt 39b3、2026-11-03、
     * human:kakimomokuri が決めた）。
     */
    @Test
    void 外部データのインフラストラクチャ層はMyBatisと使う範囲のSpringに依存してよい() {
        classes()
                .that()
                .resideInAPackage("com.example.cargotracker.externaldata.infrastructure..")
                .should()
                .onlyDependOnClassesThat(resideInAnyPackage(
                                "java..",
                                "com.example.cargotracker.externaldata..",
                                "com.example.cargotracker.shared..",
                                "org.springframework.dao..",
                                "org.springframework.transaction..",
                                "org.springframework.stereotype..",
                                "org.springframework.context.annotation..",
                                "org.apache.ibatis..")
                        .or(name(EVENT_PUBLISHER)))
                .check(PRODUCTION_CLASSES);
    }
}
