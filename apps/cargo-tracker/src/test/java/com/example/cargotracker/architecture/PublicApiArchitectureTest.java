package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.Set;
import org.junit.jupiter.api.Test;

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

    private static final String ROOT = "com.example.cargotracker.";

    /** 業務のコンテキスト（モジュールから共有カーネルと基盤を除いたもの）。 */
    private static final Set<String> CONTEXTS = BusinessContexts.all();

    /**
     * 行 12: 業務のコンテキストは、ほかの業務のコンテキストを公開 API（{@code interfaces.api}）とイベント（{@code domain.events}。どちらも
     * Spring Modulith の名前付きインターフェース）だけで使い、ほかのパッケージ（ドメインのモデル、アプリケーション、インフラストラクチャ、画面、
     * 公開 API の実装の {@code interfaces.api.internal}）に依存しない（ADR-016。Bolt 37e3 の予約 → 見積りの 1 組の規則を、Bolt 39b3b で
     * {@link BusinessContexts#all()} のすべての組に広げた。計画の「人に諮ること」4、2026-11-03、human:kakimomokuri。#50）。{@code ModularityTest}
     * の {@code allowedDependencies} は組ごとの許可を守り、この規則は名前付きインターフェースの外への依存を、名前で理由が読める規則にする。
     * コンテキストごとの規則を合わせた 1 つの規則で、違反はコンテキストごとの理由に出る。
     */
    @ArchTest
    static final ArchRule 行12_コンテキストはほかのコンテキストの公開APIとイベントのほかに依存しない = CompositeArchRule.of(
            CONTEXTS.stream().sorted().map(PublicApiArchitectureTest::rule).toList());

    private static ArchRule rule(String context) {
        return noClasses()
                .that()
                .resideInAPackage(ROOT + context + "..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        context + " のほかのコンテキストの interfaces.api と domain.events の外",
                        (JavaClass target) -> forbidden(context, target.getPackageName())))
                .because(context + " はほかのコンテキストを公開 API とイベントだけで使う（ADR-016、腐敗防止層）");
    }

    /** {@code context} のクラスが {@code packageName} のクラスに依存してはいけないか。 */
    static boolean forbidden(String context, String packageName) {
        return CONTEXTS.stream()
                .filter(other -> !other.equals(context))
                .anyMatch(other -> (packageName.equals(ROOT + other) || packageName.startsWith(ROOT + other + "."))
                        && !packageName.equals(ROOT + other + ".interfaces.api")
                        && !packageName.equals(ROOT + other + ".domain.events"));
    }

    /** 行 12 の判定そのもの（規則が今ある依存で緑のとき、許す・拒む境目を直接確かめる）。 */
    @Test
    void 行12_ほかのコンテキストは公開APIとイベントだけを許し公開APIの実装とほかのパッケージは許さない() {
        assertThat(forbidden("booking", ROOT + "quotation.interfaces.api")).isFalse();
        assertThat(forbidden("booking", ROOT + "quotation.domain.events")).isFalse();
        assertThat(forbidden("booking", ROOT + "quotation.interfaces.api.internal"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.domain.model.aggregates"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.domain.events.internal"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.application.internal.queryservices"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.infrastructure.persistence"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.interfaces.web")).isTrue();
        assertThat(forbidden("booking", ROOT + "quotation")).isTrue();
        assertThat(forbidden("identity", ROOT + "externaldata.domain.model.aggregates"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "booking.infrastructure.persistence"))
                .isFalse();
        assertThat(forbidden("booking", ROOT + "shared.domain")).isFalse();
        assertThat(forbidden("booking", ROOT + "platform.web")).isFalse();
        assertThat(forbidden("booking", ROOT + "quotationx.domain")).isFalse();
    }

    /**
     * 共有カーネルの型でも、担当範囲の外の試行の記録のポート（振る舞いを持つ。ADR-019 の例外）には依存しない（Bolt 38f の決定 (9)、
     * 計画の行 15）。上の規則は {@code shared.domain} 全体を許すので、ここで外す。
     */
    @ArchTest
    static final ArchRule 公開APIは担当範囲の外の記録のポートに依存しない = noClasses()
            .that()
            .resideInAPackage("com.example.cargotracker.*.interfaces.api")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(OutOfScopeAttemptRecorder.class.getName());
}
