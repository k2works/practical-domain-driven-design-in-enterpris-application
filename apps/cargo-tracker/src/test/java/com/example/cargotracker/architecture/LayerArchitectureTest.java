package com.example.cargotracker.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.conditions.ArchConditions.accessField;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.conditions.ArchConditions.dependOnClassesThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.identity.interfaces.web.DevTotpCodeSource;
import com.example.cargotracker.shared.annotation.ddd.Saga;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /** 範囲外の試行の記録のサービス（Bolt 34b）。 */
    private static final String SCOPE_DENIAL_SERVICE = "AdministrationScopeDenialService";

    /** 範囲外の企業への登録の試行の記録の書き手（Bolt 38b2。登録のサービスの確定の口）。 */
    private static final String COMPANY_SCOPE_DENIAL_WRITER =
            "com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationService";

    /** 範囲外の企業への登録の試行の記録を作る呼び出し（{@code AuditRecord.adminScopeDeniedCompany}）。 */
    private static final DescribedPredicate<JavaCall<?>> COMPANY_SCOPE_DENIAL =
            target(owner(equivalentTo(AuditRecord.class))).and(target(nameMatching("adminScopeDeniedCompany")));

    /** 招待の用途で発行する部品（Bolt 38b2c。DE-25 のハンドラー）。 */
    private static final String INVITATION_ISSUER =
            "com.example.cargotracker.identity.application.internal.eventhandlers.UserInvitationMailEventHandler";

    /** 招待の用途の定数の名前。 */
    private static final String INVITATION_PURPOSE = "USER_INVITATION";

    /** 担当範囲の外の試行の記録の書き手（Bolt 38f。アクセス・監査の記録のサービス）。 */
    private static final String ASSIGNED_SCOPE_DENIAL_WRITER =
            "com.example.cargotracker.identity.application.internal.commandservices.AssignedScopeDenialService";

    /** 担当範囲の外の試行の記録を作る呼び出し（{@code AuditRecord.assignedScopeDenied}）。 */
    private static final DescribedPredicate<JavaCall<?>> ASSIGNED_SCOPE_DENIAL =
            target(owner(equivalentTo(AuditRecord.class))).and(target(nameMatching("assignedScopeDenied")));

    /** 担当範囲の外の試行の記録のサービスを呼ぶアダプター（Bolt 38f。identity の公開の入口の実装）。 */
    private static final String OUT_OF_SCOPE_RECORDER_ADAPTER =
            "com.example.cargotracker.identity.interfaces.api.internal.OutOfScopeAttemptRecorderAdapter";

    /** 担当範囲の外の試行の記録のサービスの呼び出し（{@code AssignedScopeDenialService.recordDenied}）。 */
    private static final DescribedPredicate<JavaCall<?>> ASSIGNED_SCOPE_DENIAL_SERVICE_CALL = target(
                    owner(nameMatching(ASSIGNED_SCOPE_DENIAL_WRITER.replace(".", "\\."))))
            .and(target(nameMatching("recordDenied")));

    /** 担当範囲を確かめずに輸送要求を読む部品（Bolt 38f の開発レビュー M3。担当範囲の判定の部品）。 */
    private static final String STAFF_TRANSPORT_REQUEST_READER =
            "com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestScope";

    /** 社内の側の輸送要求の読み出し（担当範囲を確かめない）。 */
    private static final DescribedPredicate<JavaCall<?>> STAFF_TRANSPORT_REQUEST_READ = target(
                    owner(
                            nameMatching(
                                    "com\\.example\\.cargotracker\\.quotation\\.domain\\.model\\.aggregates\\.TransportRequestRepository")))
            .and(target(nameMatching("findByNumberForStaff")));

    /** 外部原本の採否の監査記録の書き手（Bolt 39b3b。DE-12・DE-13・DE-14 の監査の listener）。 */
    private static final String EXTERNAL_RECORD_AUDIT_WRITER =
            "com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler";

    /**
     * 外部原本の採否の監査記録を作る呼び出し（{@code AuditRecord.externalRecordAdopted}・{@code externalRecordQuarantined}、受信記録の判断の
     * {@code externalRecordDecided}、外部原本の差異の {@code externalRecordDiscrepancyFound}。判断は Bolt 39d2 の行 13、差異は Bolt 39h の行 14
     * で足した。書き手は同じ listener で増やさない）。
     */
    private static final DescribedPredicate<JavaCall<?>> EXTERNAL_RECORD_AUDIT = target(
                    owner(equivalentTo(AuditRecord.class)))
            .and(
                    target(
                            nameMatching(
                                    "externalRecordAdopted|externalRecordQuarantined|externalRecordDecided|externalRecordDiscrepancyFound")));

    /** 書き手のほかに例外がない行の印。 */
    private static final DescribedPredicate<JavaClass> NO_OTHER_EXCEPTION =
            DescribedPredicate.describe("なし", (JavaClass javaClass) -> false);

    /**
     * 「書き手は 1 か所」の表（W6 横断レビュー M-2。Bolt 39b3b の計画の「人に諮ること」4、2026-11-03、human:kakimomokuri。T-205）。書き手を 1 か所に
     * して、記録の漏れと二重の記録、ほかの道での発行を防ぐ。行ごとに、ヘルパー（{@link SoleWriter}）が「書き手（と例外）のほかは触らない」と
     * 「書き手は実在して触る」（T-135。書き手を動かす・名前を変える・触らなくなると落ちる）の 2 つの規則を作る。書き手を足すときは、規則を
     * 書かずに行を足す（規則の変更として人に諮る）。
     *
     * <p>守るのは呼び出し・定数の参照・依存で、呼び出しの行はメソッド参照（{@code AuditRecord::adminScopeDeniedCompany}）を捕まえない。
     * 招待の用途の行は {@code valueOf}・{@code values()} を捕まえず、用途の {@code switch} は形によって合成のクラスが参照して当たるか、
     * すり抜ける。用途による出し分け（Bolt 38b4）は、外から定数を参照せず、用途の型のメソッドで問い合わせる（列挙の中の参照は外す）。
     * 書き手が複数で名前の規則と閉じた一覧で守る担当範囲の外の記録のポートの部品は、この表に入れず下の規則で守る。
     */
    private static final List<SoleWriter> SOLE_WRITERS = List.of(
            // Bolt 34b・34f（IA-INV-08）。画面の層のパッケージ全体から S-20 の詳細の部品の 1 つに絞った（2026-10-19、human:kakimomokuri。
            // W6 の controller が記録のサービスを直接受けると落ちる）。組み立て（identity の合成ルート）とサービス自身は例外
            new SoleWriter(
                    "範囲外の試行の記録のサービス（S-20 の詳細の部品だけが呼ぶ）",
                    SCOPE_DENIAL_WRITER,
                    resideInAPackage("..identity.infrastructure.config..").or(simpleName(SCOPE_DENIAL_SERVICE)),
                    dependOnClassesThat(simpleName(SCOPE_DENIAL_SERVICE))),
            // Bolt 38b2b（Bolt 38b2 の終了報告の議題 2、2026-10-25、human:kakimomokuri。IA-INV-14）
            new SoleWriter(
                    "範囲外の企業への登録の試行の記録（登録のサービスだけが作る）",
                    COMPANY_SCOPE_DENIAL_WRITER,
                    NO_OTHER_EXCEPTION,
                    callMethodWhere(COMPANY_SCOPE_DENIAL)),
            // Bolt 38b2c の開始準備の決定 (4)（2026-10-26、human:kakimomokuri）。ほかの道で 24 時間のリンクを出させない。列挙の中の参照は例外
            new SoleWriter(
                    "招待の用途（招待のハンドラーだけが参照する）",
                    INVITATION_ISSUER,
                    assignableTo(PasswordResetTokenPurpose.class),
                    accessField(PasswordResetTokenPurpose.class, INVITATION_PURPOSE)),
            // Bolt 38f の決定 (5)（IA-INV-08）
            new SoleWriter(
                    "担当範囲の外の試行の記録（記録のサービスだけが作る）",
                    ASSIGNED_SCOPE_DENIAL_WRITER,
                    NO_OTHER_EXCEPTION,
                    callMethodWhere(ASSIGNED_SCOPE_DENIAL)),
            // Bolt 38f の開発レビューの低 10 (a)。アダプターを通さずにサービスを直接呼ぶと、ポートの見張りの規則を抜けられる。サービス自身は例外
            new SoleWriter(
                    "担当範囲の外の試行の記録のサービス（ポートのアダプターだけが呼ぶ）",
                    OUT_OF_SCOPE_RECORDER_ADAPTER,
                    DescribedPredicate.describe(
                            "記録のサービス自身",
                            (JavaClass javaClass) -> javaClass.getName().equals(ASSIGNED_SCOPE_DENIAL_WRITER)),
                    callMethodWhere(ASSIGNED_SCOPE_DENIAL_SERVICE_CALL)),
            // Bolt 38f の開発レビュー M3（決定 (10)「範囲を知らないまま読み書きできる道を残さない」）。予約の側の照会は 38f2 で判定の部品を通す
            // ようにし、例外から外した
            new SoleWriter(
                    "担当範囲を確かめない輸送要求の読み出し（担当範囲の判定の部品だけが呼ぶ）",
                    STAFF_TRANSPORT_REQUEST_READER,
                    NO_OTHER_EXCEPTION,
                    callMethodWhere(STAFF_TRANSPORT_REQUEST_READ)),
            // Bolt 39b3b の行 14（AUD-01、IA-INV-15）。受信記録ごとに 1 つ、イベントの時刻で書く
            new SoleWriter(
                    "外部原本の採否の監査記録（監査の listener だけが作る）",
                    EXTERNAL_RECORD_AUDIT_WRITER,
                    NO_OTHER_EXCEPTION,
                    callMethodWhere(EXTERNAL_RECORD_AUDIT)));

    /** 表の行の書き手（と例外）のほかは、行の呼び出し・参照・依存をしない（M-2。行ごとの違反は理由に行の名前が出る）。 */
    @ArchTest
    static final ArchRule 書き手のほかは触らない = CompositeArchRule.of(
            SOLE_WRITERS.stream().map(SoleWriter::othersDoNot).toList());

    /** 表の行の書き手の名前と呼び出し・参照・依存が実在し続ける（T-135。行ごとに 1 本。「A か B」にすると片方が無くても通る）。 */
    @ArchTest
    static final ArchRule 書き手は実在して触る = CompositeArchRule.of(
            SOLE_WRITERS.stream().map(SoleWriter::writerDoes).toList());

    /**
     * 書き手の表の 1 行と、行から 2 つの規則を作るヘルパー（M-2）。
     *
     * @param what 書き手だけが触るもの（違反の理由に出す）
     * @param writer 書き手のクラスの完全な名前
     * @param alsoAllowed 書き手のほかに触ってよいクラス（組み立てや、触られる側そのもの）
     * @param access 書き手だけに許す呼び出し・参照・依存
     */
    private record SoleWriter(
            String what, String writer, DescribedPredicate<JavaClass> alsoAllowed, ArchCondition<JavaClass> access) {

        ArchRule othersDoNot() {
            return noClasses()
                    .that(DescribedPredicate.describe(
                                    "書き手でない",
                                    (JavaClass javaClass) ->
                                            !javaClass.getName().equals(writer))
                            .and(not(alsoAllowed)))
                    .should(access)
                    .because(what + "。書き手は " + writer);
        }

        ArchRule writerDoes() {
            return classes()
                    .that()
                    .haveFullyQualifiedName(writer)
                    .should(access)
                    .because(what + "。書き手の名前と呼び出しが実在し続ける（T-135）");
        }
    }

    /** 担当範囲の外の試行の記録のポートを呼ぶ部品（Bolt 38f。見積りの社内の画面の層）。 */
    private static final String OUT_OF_SCOPE_RESPONDER =
            "com.example.cargotracker.quotation.interfaces.web.StaffOutOfScopeResponder";

    /** 担当範囲の外の試行の記録のポートを呼ぶ予約の部品（Bolt 38f2。予約の社内の画面の層）。 */
    private static final String BOOKING_OUT_OF_SCOPE_RESPONDER =
            "com.example.cargotracker.booking.interfaces.web.BookingOutOfScopeResponder";

    /** 担当範囲の外の試行の記録のポートを呼ぶ経路設計の部品（Bolt 38g。経路設計の社内の画面の層）。 */
    private static final String ROUTING_OUT_OF_SCOPE_RESPONDER =
            "com.example.cargotracker.routing.interfaces.web.RoutingOutOfScopeResponder";

    /** 担当範囲の外の試行の記録のポートを呼ぶ追跡の部品（Bolt 38g。追跡の社内の画面の層）。 */
    private static final String TRACKING_OUT_OF_SCOPE_RESPONDER =
            "com.example.cargotracker.tracking.interfaces.web.TrackingOutOfScopeResponder";

    /** 記録の部品の名前の規則（Bolt 38g の決定 (3)）。コンテキストの画面の層に置き、名前を {@code OutOfScopeResponder} で終える。 */
    private static final String OUT_OF_SCOPE_RESPONDER_NAME =
            "com\\.example\\.cargotracker\\..*\\.interfaces\\.web\\..*OutOfScopeResponder";

    /** 名前の規則に合ってよい部品（閉じた一覧。足すときは規則の変更として人に諮る。Bolt 38g）。 */
    private static final String OUT_OF_SCOPE_RESPONDERS = String.join(
            "|",
            OUT_OF_SCOPE_RESPONDER.replace(".", "\\."),
            BOOKING_OUT_OF_SCOPE_RESPONDER.replace(".", "\\."),
            ROUTING_OUT_OF_SCOPE_RESPONDER.replace(".", "\\."),
            TRACKING_OUT_OF_SCOPE_RESPONDER.replace(".", "\\."));

    /** 担当範囲の外の試行の記録のポートの呼び出し。 */
    private static final DescribedPredicate<JavaCall<?>> OUT_OF_SCOPE_RECORDING =
            target(owner(equivalentTo(OutOfScopeAttemptRecorder.class)));

    /**
     * 担当範囲の外の試行の記録のポートを呼ぶのは、名前の規則に合う画面の層の部品だけ（Bolt 38f の決定 (10)、ADR-019。38f2 で予約の側を、
     * 38g で経路設計と追跡の側を足し、部品の FQN の並びを名前の規則に変えた）。controller やサービスが直接呼ぶと、記録の漏れ・二重の記録や、
     * 外側のトランザクションの中の呼び出し（ポートは拒む）が起きる。
     */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートは画面の部品だけが呼ぶ = noClasses()
            .that()
            .haveNameNotMatching(OUT_OF_SCOPE_RESPONDER_NAME)
            .should()
            .callMethodWhere(OUT_OF_SCOPE_RECORDING);

    /**
     * 名前の規則に合うクラスは、閉じた一覧の部品（トップレベルで {@code @Component}）だけ（Bolt 38g。名前の規則だけでは、名前を合わせた
     * クラス（入れ子を含む）を足せば一覧が開く）。
     */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼べる名前の部品は閉じた一覧のどれか = classes()
            .that()
            .haveNameMatching(OUT_OF_SCOPE_RESPONDER_NAME)
            .should()
            .haveNameMatching(OUT_OF_SCOPE_RESPONDERS)
            .andShould()
            .beTopLevelClasses()
            .andShould()
            .beAnnotatedWith(Component.class);

    /** 呼ぶ部品の名前と呼び出しが実在し続けることの見張り（T-135。部品ごとに 1 本。「A か B」にすると片方が無くても通る）。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ部品は社内の画面の部品 = classes()
            .that()
            .haveFullyQualifiedName(OUT_OF_SCOPE_RESPONDER)
            .should()
            .callMethodWhere(OUT_OF_SCOPE_RECORDING);

    /** 予約の部品の名前と呼び出しが実在し続けることの見張り（Bolt 38f2。T-135）。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ予約の部品は予約の社内の画面の部品 = classes()
            .that()
            .haveFullyQualifiedName(BOOKING_OUT_OF_SCOPE_RESPONDER)
            .should()
            .callMethodWhere(OUT_OF_SCOPE_RECORDING);

    /** 経路設計の部品の名前と呼び出しが実在し続けることの見張り（Bolt 38g。T-135）。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ経路設計の部品は経路設計の社内の画面の部品 = classes()
            .that()
            .haveFullyQualifiedName(ROUTING_OUT_OF_SCOPE_RESPONDER)
            .should()
            .callMethodWhere(OUT_OF_SCOPE_RECORDING);

    /** 追跡の部品の名前と呼び出しが実在し続けることの見張り（Bolt 38g。T-135）。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ追跡の部品は追跡の社内の画面の部品 = classes()
            .that()
            .haveFullyQualifiedName(TRACKING_OUT_OF_SCOPE_RESPONDER)
            .should()
            .callMethodWhere(OUT_OF_SCOPE_RECORDING);

    /** ポートを呼ぶ部品はトランザクションを持たない（ポートは自分のトランザクションで書き、外側の中では拒む。決定 (4)）。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ部品はトランザクションを持たない = classes()
            .that()
            .haveNameMatching(OUT_OF_SCOPE_RESPONDER_NAME)
            .should()
            .notBeAnnotatedWith(Transactional.class);

    /** 同じく、部品のメソッドにもトランザクションを付けない。 */
    @ArchTest
    static final ArchRule 担当範囲の外の記録のポートを呼ぶ部品のメソッドはトランザクションを持たない = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .haveNameMatching(OUT_OF_SCOPE_RESPONDER_NAME)
            .should()
            .beAnnotatedWith(Transactional.class);

    /**
     * 共有カーネル（{@code shared.domain}）に置く振る舞いの型（インターフェース）は、担当範囲の外の試行の記録のポートだけ（ADR-019 の例外。
     * 決定 (9)）。ほかのポートを共有カーネルに置くと落ちる。
     */
    @ArchTest
    static final ArchRule 共有カーネルの振る舞いの型は担当範囲の外の記録のポートだけ = classes()
            .that()
            .resideInAPackage("com.example.cargotracker.shared.domain..")
            .and()
            .areInterfaces()
            .should()
            .haveFullyQualifiedName(OutOfScopeAttemptRecorder.class.getName());

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

    /**
     * 行 14: 書き手の表の規則は、監査の listener のほかが外部原本の採否の監査記録を作ると落とす（Bolt 39b3b。表の行の読み落とし（変異 V8）を
     * 捕まえる。本体の解析はテストのクラスを読まないので、ここで規則に別のクラスを読ませて確かめる）。
     */
    @Test
    void 行14_監査のlistenerのほかが外部原本の採否の監査記録を作ると書き手の表の規則が落とす() {
        JavaClasses intruder = new ClassFileImporter().importClasses(ExternalRecordAuditIntruder.class);

        EvaluationResult result = 書き手のほかは触らない.evaluate(intruder);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("externalRecordAdopted")
                .contains("externalRecordQuarantined")
                .contains("externalRecordDiscrepancyFound");
    }

    /**
     * 書き手の表の全部の行が 2 つの規則に入っている（Bolt 39b3b のステップ 7。表の行を読み落とす変異（{@code SOLE_WRITERS.stream().skip(1)}）
     * は行 14 の負例では捕まらない。規則の説明には行ごとに書き手だけが触るもの（{@code what}）が出る）。
     */
    @Test
    void 書き手の表の全部の行から規則を作る() {
        String othersDoNot = 書き手のほかは触らない.getDescription();
        String writerDoes = 書き手は実在して触る.getDescription();

        assertThat(SOLE_WRITERS).isNotEmpty().allSatisfy(row -> {
            assertThat(othersDoNot).as("書き手のほかは触らない").contains(row.what() + "。書き手は " + row.writer());
            assertThat(writerDoes).as("書き手は実在して触る").contains(row.what() + "。書き手の名前と呼び出しが実在し続ける");
        });
    }

    /** 行 14 の変異のためのクラス（監査の listener でないのに外部原本の採否の監査記録を作る）。呼ばれることはない。 */
    static final class ExternalRecordAuditIntruder {

        private ExternalRecordAuditIntruder() {}

        static Object adopt() {
            return AuditRecord.externalRecordAdopted(null, null, null, null, null);
        }

        static Object quarantine() {
            return AuditRecord.externalRecordQuarantined(null, null, null, null, null, null);
        }

        static Object discrepancy() {
            return AuditRecord.externalRecordDiscrepancyFound(null, null, null, null, null, null);
        }
    }
}
