package com.example.cargotracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * AT-05: 業務のルールの層のステップ定義（{@code <コンテキスト>.acceptance}）は、自分のコンテキストのアプリケーション層・ドメイン層と
 * 共有カーネル、他のコンテキストの公開 API（{@code interfaces.api}）にだけ依存し、自分のインフラストラクチャ層・画面
 * （{@code interfaces.web}）・公開 API の実装（{@code interfaces.api.internal}）と他のコンテキストの内部に依存しない（テスト戦略。
 * Bolt 28 で、いまのステップ定義の実態に合わせて作った。入力ポートのほかにドメインの値オブジェクト・集約でコマンドを組み立て、結果を
 * 確かめることは認める）。画面の層のステップ定義（{@code ui}）と、受入テストの組み立て（{@code acceptance}・{@code shared.acceptance}。
 * メモリのリポジトリや公開 API の実装をつなぐのが役目）は、この依存の規則の対象にしない。画面の層のステップ定義には、Playwright の正規表現の
 * 規則（T-128。{@code Pattern.quote} を呼ばない）だけを当てる。エスケープせずに文字列を正規表現に連結することは見張れないので、
 * {@code PlaywrightText} を使う約束で補う。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class AcceptanceStepArchitectureTest {

    private static final String ROOT = "com.example.cargotracker.";
    private static final List<String> CONTEXTS =
            List.of("quotation", "routing", "booking", "tracking", "identity", "notification", "externaldata");

    /** 依存元のステップ定義のコンテキストを決め、そのコンテキストに許さない依存を違反にする。 */
    private static final ArchCondition<JavaClass> DEPEND_ONLY_ON_OWN_CONTEXT_AND_PUBLIC_APIS =
            new ArchCondition<>("自分のインフラストラクチャ層・画面・公開 API の実装と、他のコンテキストの公開 API 以外に依存しない") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    contextOf(item.getPackageName())
                            .ifPresent(context -> item.getDirectDependenciesFromSelf().stream()
                                    .filter(dependency -> forbidden(
                                            context, dependency.getTargetClass().getPackageName()))
                                    .forEach(dependency -> events.add(
                                            SimpleConditionEvent.violated(dependency, dependency.getDescription()))));
                }
            };

    /**
     * 行 13: 業務のルールの層のステップ定義（{@code <コンテキスト>.acceptance}）すべてに当てる 1 つの規則（Bolt 39b3b の計画の「人に諮ること」5。2026-11-03、
     * human:kakimomokuri）。コンテキストごとに手書きした規則の写しをやめ、依存元のパッケージからコンテキストを決める。コンテキストを足しても
     * 規則を書き足さずに済み、acceptance のないコンテキスト（通知）に空の規則を作って落ちること（ArchUnit の既定の failOnEmptyShould）もない。
     * 業務のコンテキストでない {@code shared.acceptance}（受入テストの組み立て）は対象にしない。
     */
    @ArchTest
    static final ArchRule 行13_業務のルールの層のステップ定義は自分のコンテキストと公開APIだけに依存する = classes()
            .that()
            .resideInAPackage(ROOT + "*.acceptance..")
            .should(DEPEND_ONLY_ON_OWN_CONTEXT_AND_PUBLIC_APIS)
            .because("AT-05: ステップ定義は自分の入力ポート・ドメインと他のコンテキストの公開 API だけを使う（テスト戦略）");

    /**
     * 画面の層のステップは {@code Pattern.quote} を呼ばない（Bolt 33c、Try T-128）。Playwright は正規表現をブラウザーの JavaScript で照合し、
     * {@code \Q…\E} が効かない（Bolt 5・25・33a で踏んだ）。文字列は {@code PlaywrightText} でエスケープする。対象のクラスがなければ
     * ArchUnit が失敗する（空振りしない）。
     */
    @ArchTest
    static final ArchRule 画面の層のステップはPatternのquoteを呼ばない = noClasses()
            .that()
            .resideInAPackage(ROOT + "ui..")
            .should()
            .callMethod(java.util.regex.Pattern.class, "quote", String.class)
            .because("Playwright は正規表現を JavaScript で照合するので、PlaywrightText.literal でエスケープする（T-128）");

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

    /** 行 13: 依存元のパッケージからコンテキストを決める。業務のコンテキストでないもの（共有カーネル、ルート）はコンテキストを持たない。 */
    @Test
    void 行13_ステップ定義のコンテキストは依存元のパッケージの最初の名前で業務のコンテキストに限る() {
        assertThat(contextOf(ROOT + "booking.acceptance")).contains("booking");
        assertThat(contextOf(ROOT + "externaldata.acceptance.sub")).contains("externaldata");
        assertThat(contextOf(ROOT + "notification.acceptance")).contains("notification");
        assertThat(contextOf(ROOT + "shared.acceptance")).isEmpty();
        assertThat(contextOf(ROOT + "acceptance")).isEmpty();
    }

    /**
     * 行 13: コンテキストの一覧は、業務のコンテキスト（モジュールから共有カーネルと基盤を除いたもの。通知を含む 7 つ）に一致する（W6 横断レビュー
     * M-3。Bolt 39）。一覧（{@code CONTEXTS}）は規則のコンテキストの判定と、他のコンテキストへの依存の判定に使う。
     */
    @Test
    void 行13_コンテキストの一覧は業務のコンテキストに一致する() {
        assertThat(BusinessContexts.all()).containsExactlyInAnyOrderElementsOf(CONTEXTS);
    }

    /** {@code packageName} のステップ定義が属する業務のコンテキスト（{@code ROOT} の次の名前）。 */
    static Optional<String> contextOf(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return Optional.empty();
        }
        String first = packageName.substring(ROOT.length()).split("\\.", 2)[0];
        return CONTEXTS.contains(first) ? Optional.of(first) : Optional.empty();
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
