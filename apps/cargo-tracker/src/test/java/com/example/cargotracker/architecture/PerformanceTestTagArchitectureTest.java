package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;

/**
 * 性能の計測のテストの置き場所とタグ（Bolt 39b4 の行 8）。性能の計測は {@code performance} のタグで {@code test} から外し、
 * {@code performanceTest} で動かす（{@code check} と push の CI に入れず、CI の手動のジョブ {@code performance} で動かす）。タグで選ぶ規則にすると、タグを外したテストが対象から消えて規則が
 * 空のまま通るので、名前（{@code *PerformanceTest}）で選び、対象がなければ落とす。テストのクラスも読む（{@code DoNotIncludeTests} を
 * 付けない。{@code TestPlacementArchitectureTest} の前例）。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class PerformanceTestTagArchitectureTest {

    private static final String PERFORMANCE = "performance";

    /** 非機能要件の追跡番号のタグの始まり（test_strategy の {@code @NFR-<ID>} の規約。例 {@code NFR-PERF-06}）。 */
    private static final String NFR_PREFIX = "NFR-";

    @ArchTest
    static final ArchRule 名前がPerformanceTestのクラスは統合テストに置きperformanceとNFRのタグを持つ = classes()
            .that()
            .haveSimpleNameEndingWith("PerformanceTest")
            .should()
            .resideInAPackage("..integration..")
            .andShould(havePerformanceAndNfrTags())
            .allowEmptyShould(false);

    private static ArchCondition<JavaClass> havePerformanceAndNfrTags() {
        return new ArchCondition<>("have the tags " + PERFORMANCE + " and " + NFR_PREFIX + "<ID>") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> tags = Arrays.stream(item.reflect().getAnnotationsByType(Tag.class))
                        .map(Tag::value)
                        .toList();
                boolean satisfied =
                        tags.contains(PERFORMANCE) && tags.stream().anyMatch(tag -> tag.startsWith(NFR_PREFIX));
                events.add(new SimpleConditionEvent(item, satisfied, item.getName() + " のタグ " + tags));
            }
        };
    }
}
