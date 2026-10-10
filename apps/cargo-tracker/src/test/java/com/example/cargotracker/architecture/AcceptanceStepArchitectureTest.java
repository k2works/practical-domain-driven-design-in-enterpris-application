package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AT-05: 業務のルールの層のステップ定義（{@code <コンテキスト>.acceptance}）は、自分のコンテキストのアプリケーション層・ドメイン層と
 * 共有カーネル、他のコンテキストの公開 API（{@code interfaces.api}）にだけ依存し、自分のインフラストラクチャ層・画面
 * （{@code interfaces.web}）・公開 API の実装（{@code interfaces.api.internal}）と他のコンテキストの内部に依存しない（テスト戦略。
 * Bolt 28 で、いまのステップ定義の実態に合わせて作った。入力ポートのほかにドメインの値オブジェクト・集約でコマンドを組み立て、結果を
 * 確かめることは認める）。画面の層のステップ定義（{@code ui}）と、受入テストの組み立て（{@code acceptance}・{@code shared.acceptance}。
 * メモリのリポジトリや公開 API の実装をつなぐのが役目）は対象にしない。
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

    /** 規則の判定そのもの（他のコンテキストの公開 API へ依存するステップ定義がまだないため、判定を直接確かめる。Bolt 28 の開発レビュー）。 */
    @Test
    void 他のコンテキストは公開APIだけを許し自分のインフラストラクチャ層と画面と公開APIの実装は許さない() {
        assertThat(forbidden("booking", ROOT + "quotation.interfaces.api")).isFalse();
        assertThat(forbidden("booking", ROOT + "quotation.interfaces.api.internal"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "quotation.domain.model.valueobjects"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "tracking.domain.events")).isTrue();
        assertThat(forbidden("booking", ROOT + "booking.domain.model.aggregates"))
                .isFalse();
        assertThat(forbidden("booking", ROOT + "booking.application.internal.commandservices"))
                .isFalse();
        assertThat(forbidden("booking", ROOT + "booking.infrastructure.persistence"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "booking.interfaces.web")).isTrue();
        assertThat(forbidden("booking", ROOT + "booking.interfaces.api.internal"))
                .isTrue();
        assertThat(forbidden("booking", ROOT + "shared.domain")).isFalse();
    }

    private static ArchRule rule(String context) {
        return noClasses()
                .that()
                .resideInAPackage(ROOT + context + ".acceptance..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        context + " のインフラストラクチャ層・画面・公開 API の実装、または他のコンテキストの公開 API 以外",
                        (JavaClass target) -> forbidden(context, target.getPackageName())))
                .because("AT-05: ステップ定義は自分の入力ポート・ドメインと他のコンテキストの公開 API だけを使う（テスト戦略）");
    }

    /** {@code context} のステップ定義が {@code packageName} のクラスに依存してはいけないか。 */
    static boolean forbidden(String context, String packageName) {
        String own = ROOT + context + ".";
        if (packageName.startsWith(own + "infrastructure")
                || packageName.startsWith(own + "interfaces.web")
                || packageName.startsWith(own + "interfaces.api.internal")) {
            return true;
        }
        return CONTEXTS.stream()
                .filter(other -> !other.equals(context))
                .anyMatch(other -> packageName.startsWith(ROOT + other + ".")
                        && !packageName.equals(ROOT + other + ".interfaces.api"));
    }
}
