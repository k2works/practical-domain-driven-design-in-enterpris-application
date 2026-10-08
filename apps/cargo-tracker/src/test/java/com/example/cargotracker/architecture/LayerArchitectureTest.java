package com.example.cargotracker.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * AT-02: 依存は interfaces → application → domain ← infrastructure の向きに限る（バックエンドアーキテクチャ）。
 * サービスを組み立てる infrastructure.config（合成ルート）だけが、例外として application に依存してよい（D-5）。
 *
 * <p>層の規則（layeredArchitecture）は層の間の依存だけを見る。層の外（platform、ルートのパッケージ、外部のライブラリ）への
 * 依存は、このクラスの残りの規則で見る。コンテキストの間の依存は Spring Modulith の検証（AT-03、ModularityTest）が見る。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    /** 顧客 Web の URL（{@code /customer/}）の下に割り当てたコントローラー。 */
    private static final DescribedPredicate<JavaClass> CUSTOMER_WEB_CONTROLLERS =
            new DescribedPredicate<>("顧客 Web（/customer/**）のコントローラー") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.isAnnotatedWith(Controller.class)
                            && javaClass
                                    .tryGetAnnotationOfType(RequestMapping.class)
                                    .map(mapping -> Arrays.stream(mapping.value())
                                            .anyMatch(path -> path.startsWith("/customer")))
                                    .orElse(false);
                }
            };

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

    /** 合成ルートが、アダプターや業務の処理の逃げ場にならないようにする。 */
    @ArchTest
    static final ArchRule 合成ルートにはConfigurationのクラスだけを置く = classes()
            .that()
            .resideInAPackage("..infrastructure.config..")
            .should()
            .beAnnotatedWith(Configuration.class);

    /** interfaces はドメインの型を読んでよいが、送信ポート（リポジトリ）を直接使って application を迂回しない。 */
    @ArchTest
    static final ArchRule interfacesはリポジトリに依存しない = noClasses()
            .that()
            .resideInAPackage("..interfaces..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    /**
     * 顧客 Web（{@code /customer/**}）のコントローラーは、荷主企業で絞らない社内用の照会（{@code Staff…}）を使わない。
     * 荷主の画面の照会は必ず荷主企業で絞る（Q-INV-08、Bolt 4 R-02、Bolt 5 R-10。Bolt 6〜8 レビュー R-16 で規則にした）。
     */
    @ArchTest
    static final ArchRule 顧客Webのコントローラーは社内用の照会を使わない = noClasses()
            .that(CUSTOMER_WEB_CONTROLLERS)
            .should()
            .dependOnClassesThat()
            .haveSimpleNameStartingWith("Staff");

    /**
     * アプリケーションサービス（入力ポートの実装）は {@code @Service} を付ける。JIG がユースケースとして読むための印で、
     * 組み立ては infrastructure.config の {@code @Bean} が担う。application は部品探索の対象から外している
     * （CargoTrackerApplication。2026-10-03 の人の決定）。
     */
    @ArchTest
    static final ArchRule アプリケーションサービスはServiceの印を付ける = classes()
            .that()
            .haveNameMatching(".*\\.application\\.internal\\.(commandservices|queryservices)\\.[^.$]+Service"
                    + "|.*\\.application\\.internal\\.eventhandlers\\.[^.$]+EventHandler")
            .should()
            .beAnnotatedWith(Service.class);

    /** application は Spring の部品探索から切り離し、Web・永続化の技術に依存しない（バックエンドアーキテクチャ）。 */
    @ArchTest
    static final ArchRule applicationはWebと永続化の技術に依存しない = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.jdbc..",
                    "org.apache.ibatis..",
                    "org.mybatis..",
                    "jakarta.servlet..");

    /**
     * platform の部品は設定を通してだけ使う。コンテキストのコードから参照しない（バックエンドアーキテクチャ）。
     * 例外は画面の表示の部品（platform.web）で、次の規則のとおり画面の層からだけ参照できる（Bolt 22）。
     */
    @ArchTest
    static final ArchRule platformはほかから参照されない = noClasses()
            .that()
            .resideOutsideOfPackage("..platform..")
            .should()
            .dependOnClassesThat(resideInAPackage("..platform..").and(not(resideInAPackage("..platform.web.."))));

    /** 画面の表示の部品（platform.web）は、コンテキストの画面の層（interfaces.web）からだけ参照する（Bolt 22、#41）。 */
    @ArchTest
    static final ArchRule platformの表示の部品は画面の層からだけ参照される = noClasses()
            .that()
            .resideOutsideOfPackages("..platform..", "..interfaces.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..platform.web..");

    /**
     * 日時表示・期間表示は platform.web の部品を使い、画面の層に写しを作らない（Bolt 22、#41。Bolt 21 の Try T-63）。
     * 書式（DateTimeFormatter）を持ってよいのは、入力を解釈するフォームの変換だけ。タイムゾーンは部品の {@code ZONE} を使う。
     */
    @ArchTest
    static final ArchRule 画面の層は日時表示の写しを持たない = noClasses()
            .that()
            .resideInAPackage("..interfaces.web..")
            .and()
            .haveSimpleNameNotEndingWith("FormConverter")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(DateTimeFormatter.class)
            .orShould()
            .callMethod(ZoneId.class, "of", String.class);

    /** platform は技術の部品だけを置き、コンテキストの型に依存しない。 */
    @ArchTest
    static final ArchRule platformはコンテキストに依存しない = classes()
            .that()
            .resideInAPackage("..platform..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "org.apache.ibatis..", "com.example.cargotracker.platform..");
}
