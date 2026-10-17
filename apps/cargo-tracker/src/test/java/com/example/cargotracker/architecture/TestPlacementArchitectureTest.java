package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * テストの部品の置き場所（Bolt 34c）。テストのクラスも読む（{@code AcceptanceStepArchitectureTest} の前例。{@code DoNotIncludeTests} を
 * 付けない）。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class TestPlacementArchitectureTest {

    private static final String SPY_BASE =
            "com.example.cargotracker.identity.infrastructure.security.IdentitySpyIntegrationTestBase";

    /**
     * AT-10: identity の統合テストは、spy（{@code @MockitoSpyBean}）のフィールドを共通の基底にだけ宣言する（Bolt 32d の約束）。テストの
     * クラスごとに spy の組が違うと、Spring のコンテキストがクラスごとに作られて増える。対象のフィールドがなければ落ちる（基底の spy が
     * 数えられていることを先に確かめる。T-119）。範囲は identity のパッケージのフィールドだけで、ほかのパッケージ（integration）と、
     * クラスに付ける {@code @MockitoSpyBean(types = …)} は対象にしない。コンテキストを分けるほかの要因（{@code @MockitoBean}・
     * {@code @TestPropertySource} など）も見ない（spy の約束だけを見張る。Bolt 34c の開発レビュー）。
     */
    @ArchTest
    static final ArchRule identityのspyは共通の基底にだけ宣言する = fields().that()
            .areAnnotatedWith(MockitoSpyBean.class)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.example.cargotracker.identity..")
            .should()
            .beDeclaredIn(SPY_BASE)
            .allowEmptyShould(false);
}
