package com.example.cargotracker.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.example.cargotracker.identity.interfaces.web.DevTotpCodeSource;
import com.example.cargotracker.shared.annotation.ddd.Saga;
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
 * サービスを組み立てる infrastructure.config（合成ルート）だけが、例外として application に依存してよい（D-5）。ほかの例外は
 * サガの状態の永続化（ADR-015）と、identity の認証の入口から identity のコマンドサービスへの依存（Bolt 31、ADR-012）。
 *
 * <p>層の規則（layeredArchitecture）は層の間の依存だけを見る。層の外（platform、ルートのパッケージ、外部のライブラリ）への
 * 依存は、このクラスの残りの規則で見る。コンテキストの間の依存は Spring Modulith の検証（AT-03、ModularityTest）が見る。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    /** 時刻の取り決めの {@code @Configuration}（合成ルートの外に置く唯一の例外。AT-11）。 */
    private static final String CLOCK_CONFIGURATION = "com.example.cargotracker.ClockConfiguration";

    /** Spring Security の認証の入口（D-5 の認証の例外。Bolt 31）。 */
    private static final String IDENTITY_AUTHENTICATION_ENTRY =
            "com.example.cargotracker.identity.infrastructure.security..";

    private static final String IDENTITY_SESSION_KEYS =
            "com.example.cargotracker.identity.application.internal.session..";

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
            .mayNotBeAccessedByAnyLayer()
            // 他のコンテキストの公開 API（開発ガイドライン第 3 章のインターフェース層の <コンテキスト>.interfaces.api）は、呼ぶ側の腐敗防止層と、
            // 公開 API を腐敗防止層に渡す合成ルートからだけ参照してよい（Bolt 25 の人の決定。公開 API の型はインターフェースと record で、
            // 実装は interfaces.api.internal に置く。見積りの公開 API も 2026-10-09 に移した）
            .ignoreDependency(
                    resideInAnyPackage("..application.internal.outboundservices.acl..", "..infrastructure.config.."),
                    resideInAPackage("com.example.cargotracker.*.interfaces.api"))
            // dev の A-02 の入力済みのコード（Bolt 31、ADR-011 の dev の 1 層目）。画面の層の関数の型に dev だけの部品をつなぐのは、
            // identity の合成ルートだけに許す（2026-10-13 の人の決定。画面の層は infrastructure を参照しない）
            .ignoreDependency(
                    resideInAPackage("com.example.cargotracker.identity.infrastructure.config.."),
                    equivalentTo(DevTotpCodeSource.class));

    /**
     * 合成ルートの外の infrastructure は application に依存しない（D-5）。例外はサガの状態の永続化で、永続化の実装
     * （{@code infrastructure.persistence}）だけが {@code application.sagas} のサガとリポジトリ（送信ポート）を参照してよい。
     * サガはアプリケーション層に置く（開発ガイドライン第 3 章、ADR-015。Bolt 23 の人の指摘）。
     */
    @ArchTest
    static final ArchRule 合成ルートの外のinfrastructureはapplicationに依存しない = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .and()
            .resideOutsideOfPackages("..infrastructure.config..", IDENTITY_AUTHENTICATION_ENTRY)
            .should()
            .dependOnClassesThat(
                    resideInAPackage("..application..").and(not(resideInAPackage("..application.sagas.."))))
            .because("サガの状態の永続化だけを例外にする（D-5、ADR-015）");

    /**
     * D-5 の認証の例外（Bolt 31、ADR-012）。Spring Security の認証の入口（provider・listener）は {@code identity.infrastructure.security}
     * に置き、業務の判断（TOTP の照合と刻みの使用、Bolt 32 のロックの数え方）は {@code identity.application.internal.commandservices}
     * に置く。入口から参照してよいのはそのコマンドサービスと、画面と共有する session の属性の名前と URL（{@code application.internal.session}。
     * ふるまいを持たない。Bolt 33c、ADR-012 の決定 6）だけで、ほかのアプリケーション層には依存しない（規則の名前は ADR が参照するので変えない）。
     * 定数（{@code static final String}）はコンパイル時に参照先に埋め込まれるので、この規則と層の規則には見えない。この例外が守るのは
     * メソッドの呼び出しと Bean の流入で、定数の置き場所の向き（内側から画面の層に依存しない）は設計の約束として保つ（Bolt 33f の開発レビュー）。
     */
    @ArchTest
    static final ArchRule 認証の入口はidentityのコマンドサービスだけに依存してよい = noClasses()
            .that()
            .resideInAPackage(IDENTITY_AUTHENTICATION_ENTRY)
            .should()
            .dependOnClassesThat(resideInAPackage("..application..")
                    .and(not(resideInAnyPackage(
                            "com.example.cargotracker.identity.application.internal.commandservices..",
                            "com.example.cargotracker.identity.application.internal.session.."))))
            .because("認証の入口の例外は identity のコマンドサービスと、session の属性の名前と URL を認証の入口と画面で共有するため"
                    + "（ふるまいを持たない。Bolt 33c）の session に限る（D-5、ADR-012 の決定 6、Bolt 31）");

    /**
     * 上の例外の根拠（{@code application.internal.session} はふるまいを持たない名前と URL だけ）を見張る（Bolt 33c の開発レビュー）。
     * メソッドや Spring の部品が入ると、認証の入口に業務の判断が持ち込まれて例外が黙って広がる。
     */
    @ArchTest
    static final ArchRule 認証の入口と画面が共有するsessionの名前はふるまいを持たない定数だけ = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(IDENTITY_SESSION_KEYS)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage(IDENTITY_SESSION_KEYS)
            .allowEmptyShould(true)
            .because("例外を許した根拠はふるまいを持たないこと（ADR-012 の決定 6、Bolt 33c）");

    @ArchTest
    static final ArchRule 認証の入口と画面が共有するsessionの名前のフィールドは定数だけ = fields().that()
            .areDeclaredInClassesThat()
            .resideInAPackage(IDENTITY_SESSION_KEYS)
            .should()
            .beStatic()
            .andShould()
            .beFinal()
            .because("名前と URL の定数だけを置く（Bolt 33c）");

    @ArchTest
    static final ArchRule 認証の入口と画面が共有するsessionの名前はSpringの部品にしない = noClasses()
            .that()
            .resideInAPackage(IDENTITY_SESSION_KEYS)
            .should()
            .beMetaAnnotatedWith(org.springframework.stereotype.Component.class)
            .allowEmptyShould(true)
            .because("Bean にすると、認証の入口からふるまいを呼べるようになる（Bolt 33c）");

    @ArchTest
    static final ArchRule サガの状態はinfrastructureの永続化の実装からだけ参照される = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .and()
            .resideOutsideOfPackages("..infrastructure.config..", "..infrastructure.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application.sagas..");

    /**
     * 公開 API の実装（インバウンドアダプター。{@code interfaces.api.internal}）は部品探索で拾い、型では誰からも参照しない。他のコンテキストは
     * 公開 API のインターフェース（{@code interfaces.api}）だけに依存する。Spring Modulith の名前付きインターフェースが子のパッケージを
     * 含むかどうかに左右されずに、アダプターへの直接の依存を止める（Bolt 25 レビュー A-4）。
     */
    @ArchTest
    static final ArchRule 公開APIの実装はほかのクラスから参照されない = noClasses()
            .that()
            .resideOutsideOfPackage("..interfaces.api.internal..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..interfaces.api.internal..");

    /** サガは業務の流れ（Business Flows）をアプリケーション層で進める（開発ガイドライン第 1 章・第 3 章の {@code application.sagas}）。 */
    @ArchTest
    static final ArchRule サガはアプリケーション層のsagasに置く =
            classes().that().areAnnotatedWith(Saga.class).should().resideInAPackage("..application.sagas..");

    /** 合成ルートが、アダプターや業務の処理の逃げ場にならないようにする。 */
    @ArchTest
    static final ArchRule 合成ルートにはConfigurationのクラスだけを置く = classes()
            .that()
            .resideInAPackage("..infrastructure.config..")
            .should()
            .beAnnotatedWith(Configuration.class);

    /**
     * 逆向き（AT-11。Bolt 34c）: 本体の {@code @Configuration} は合成ルート（infrastructure.config）に置く。直接の注釈だけを見る
     * （{@code @SpringBootApplication} とテストの {@code @TestConfiguration} はメタ注釈として持つので対象にしない）。ルートの
     * {@code ClockConfiguration} は、どのコンテキストにも属さない時刻の横断の取り決め（BR-10・ADR-007）なので例外にする
     * （2026-10-17、human:kakimomokuri）。
     */
    @ArchTest
    static final ArchRule 本体のConfigurationは合成ルートに置く = classes()
            .that()
            .areAnnotatedWith(Configuration.class)
            .should()
            .resideInAPackage("..infrastructure.config..")
            .orShould()
            .haveFullyQualifiedName(CLOCK_CONFIGURATION);

    /**
     * 上の例外の名前が実在し続けること（T-135）: ルートのパッケージの直接の {@code @Configuration} は {@code ClockConfiguration} だけで、
     * なければ落ちる。{@code ClockConfiguration} を動かしたり名前を変えたりして例外が古くなったときに知らせる。例外の広がり（ルートに
     * 別の {@code @Configuration} を足す）は、上の規則が完全な名前 1 つしか許さないことで止める（Bolt 34c の開発レビュー）。
     */
    @ArchTest
    static final ArchRule ルートのConfigurationは時刻の取り決めだけ = classes()
            .that()
            .areAnnotatedWith(Configuration.class)
            .and()
            .resideInAPackage("com.example.cargotracker")
            .should()
            .haveFullyQualifiedName(CLOCK_CONFIGURATION)
            .allowEmptyShould(false);

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
                    "jakarta.servlet..",
                    "org.springframework.mail..",
                    "jakarta.mail..");

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
     * 日時表示は platform.web の部品を使い、画面の層に写しを作らない（Bolt 22、#41。Bolt 21 の Try T-63）。この規則が守らせるのは
     * 日時の書式とタイムゾーンで、期間の写し（「時間」「分」の文言の組み立て）は開発レビューで見る。
     * 書式（DateTimeFormatter）を持ってよいのは、入力を解釈するフォームの変換（名前が FormConverter で終わる）だけ。
     * タイムゾーンは部品の {@code ZONE} を使い、{@code ZoneId.of}・{@code ZoneId.systemDefault}・{@code ZoneOffset.of*} を
     * 画面の層で呼ばない（サーバーのタイムゾーンに依存する事故を防ぐ。Bolt 22 レビュー）。
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
            .callMethodWhere(target(owner(assignableTo(ZoneId.class))).and(target(nameMatching("of.*|systemDefault"))));

    /** 範囲外の試行の記録の書き手（Bolt 34f。S-20 の詳細の部品）。 */
    private static final String SCOPE_DENIAL_WRITER = "com.example.cargotracker.identity.interfaces.web.UserDetailPage";

    /**
     * 範囲外の試行の記録のサービス（Bolt 34b）は、S-20 の詳細の部品（とその組み立て）だけが呼ぶ。書き手を 1 か所にして、記録の漏れと
     * 二重の記録を防ぐ（IA-INV-08、Bolt 34b の開発レビュー）。Bolt 34f で、画面の層のパッケージ全体から部品の 1 つに絞った
     * （2026-10-19、human:kakimomokuri。W6 の controller が記録のサービスを直接受けると落ちる）。
     */
    @ArchTest
    static final ArchRule 範囲外の試行の記録は詳細の部品からだけ呼ぶ = noClasses()
            .that()
            .resideOutsideOfPackages("..identity.infrastructure.config..")
            .and()
            .doNotHaveFullyQualifiedName(SCOPE_DENIAL_WRITER)
            .and()
            .doNotHaveSimpleName("AdministrationScopeDenialService")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("AdministrationScopeDenialService");

    /** 例外の名前が実在し続けることの見張り。部品を動かしたり名前を変えたりすると落ちる（T-135）。 */
    @ArchTest
    static final ArchRule 範囲外の試行の記録の書き手は詳細の部品 = classes()
            .that()
            .haveFullyQualifiedName(SCOPE_DENIAL_WRITER)
            .should()
            .dependOnClassesThat()
            .haveSimpleName("AdministrationScopeDenialService");

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
