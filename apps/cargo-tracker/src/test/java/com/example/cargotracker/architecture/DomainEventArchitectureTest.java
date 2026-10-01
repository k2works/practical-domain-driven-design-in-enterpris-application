package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ドメインイベントの置き場所と形（開発戦略の注釈の語彙、D-1）。
 * イベントは各コンテキストの domain.events（公表された言語）に、注釈 @DomainEvent を付けた record として置く。
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
    static final ArchRule domain_eventsのクラスはDomainEventの付いたrecordである = classes()
            .that()
            .resideInAPackage("..domain.events..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .beAnnotatedWith(DomainEvent.class)
            .andShould()
            .beRecords();
}
