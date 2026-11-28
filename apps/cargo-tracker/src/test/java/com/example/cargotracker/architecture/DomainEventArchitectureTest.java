package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ドメインイベントの置き場所と形（開発戦略の注釈の語彙、D-1）。
 * イベントは各コンテキストの domain.events（公表された言語）に、注釈 @DomainEvent を付けた record として置く。
 * イベントが持つ専用の値（理由の区分など）は、イベントの record の入れ子にする（トップレベルには置かない）。
 */
@AnalyzeClasses(packages = "com.example.cargotracker", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainEventArchitectureTest {

    @ArchTest
    static final ArchRule DomainEventの付いたクラスはdomain_eventsにあるrecordである = classes()
            .that()
            .areAnnotatedWith(DomainEvent.class)
            .should()
            .resideInAPackage("..domain.events..")
            .andShould()
            .beRecords();

    @ArchTest
    static final ArchRule domain_eventsのトップレベルのクラスはDomainEventの付いたrecordである = classes()
            .that()
            .resideInAPackage("..domain.events..")
            .and()
            .areTopLevelClasses()
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beAnnotatedWith(DomainEvent.class)
            .andShould()
            .beRecords();

    /**
     * 保存されるイベントの形は契約である。コンテキストのドメインモデルの型を持つと、モデルのリファクタリングで
     * 保存済みのイベントを復元できなくなるため、Java の標準と共有カーネルの型だけで表す（バックエンドアーキテクチャ）。
     */
    @ArchTest
    static final ArchRule ドメインイベントはJava標準と共有カーネルの型だけを持つ = classes()
            .that()
            .resideInAPackage("..domain.events..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.example.cargotracker.shared.domain..",
                    "com.example.cargotracker.shared.annotation.ddd..",
                    "com.example.cargotracker..domain.events..");

    /**
     * 共有カーネルの型でも、担当範囲の外の試行の記録のポート（振る舞いを持つ。ADR-019 の例外）には依存しない（Bolt 38f の決定 (9)、
     * 計画の行 15）。上の規則は {@code shared.domain} 全体を許すので、ここで外す。
     */
    @ArchTest
    static final ArchRule ドメインイベントは担当範囲の外の記録のポートに依存しない = noClasses()
            .that()
            .resideInAPackage("..domain.events..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(OutOfScopeAttemptRecorder.class.getName());
}
