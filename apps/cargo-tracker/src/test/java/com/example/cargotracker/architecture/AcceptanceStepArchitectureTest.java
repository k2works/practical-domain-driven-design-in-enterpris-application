package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * AT-05: 業務のルールの層のステップ定義（{@code <コンテキスト>.acceptance}）は、自分のコンテキストのアプリケーション層・ドメイン層と
 * 共有カーネル、他のコンテキストの公開 API（{@code interfaces.api}）にだけ依存し、自分のインフラストラクチャ層・画面
 * （{@code interfaces.web}）と他のコンテキストの内部に依存しない（テスト戦略。Bolt 28 で、いまのステップ定義の実態に合わせて作った。
 * 入力ポートのほかにドメインの値オブジェクト・集約でコマンドを組み立て、結果を確かめることは認める）。画面の層のステップ定義
 * （{@code ui}）は画面を通すので対象にしない。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class AcceptanceStepArchitectureTest {

    private static final String ROOT = "com.example.cargotracker.";
    private static final List<String> CONTEXTS = List.of("quotation", "routing", "booking", "tracking", "identity");

    @ArchTest
    static final ArchRule 見積りのステップ定義は自分のコンテキストと公開APIだけに依存する = rule("quotation");

    @ArchTest
    static final ArchRule 経路設計のステップ定義は自分のコンテキストと公開APIだけに依存する = rule("routing");

    @ArchTest
    static final ArchRule 予約のステップ定義は自分のコンテキストと公開APIだけに依存する = rule("booking");

    @ArchTest
    static final ArchRule 追跡のステップ定義は自分のコンテキストと公開APIだけに依存する = rule("tracking");

    @ArchTest
    static final ArchRule アクセス監査のステップ定義は自分のコンテキストと公開APIだけに依存する = rule("identity");

    private static ArchRule rule(String context) {
        return noClasses()
                .that()
                .resideInAPackage(ROOT + context + ".acceptance..")
                .should()
                .dependOnClassesThat(forbiddenFrom(context))
                .because("AT-05: ステップ定義は自分の入力ポート・ドメインと他のコンテキストの公開 API だけを使う（テスト戦略）");
    }

    /** 自分のインフラストラクチャ層と画面、他のコンテキストの公開 API 以外。 */
    private static DescribedPredicate<JavaClass> forbiddenFrom(String context) {
        return DescribedPredicate.describe(context + " のインフラストラクチャ層・画面、または他のコンテキストの公開 API 以外", target -> {
            String name = target.getPackageName();
            if (name.startsWith(ROOT + context + ".infrastructure")
                    || name.startsWith(ROOT + context + ".interfaces.web")) {
                return true;
            }
            return CONTEXTS.stream()
                    .filter(other -> !other.equals(context))
                    .anyMatch(other ->
                            name.startsWith(ROOT + other + ".") && !name.equals(ROOT + other + ".interfaces.api"));
        });
    }
}
