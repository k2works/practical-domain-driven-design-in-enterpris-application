package com.example.cargotracker.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleTag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 受入条件とシナリオの照合（テスト戦略「CI で受入条件の数と照合して確かめる（ADR-009 のコンプライアンス）」。Bolt 2 レビュー R-17 の
 * 持ち越しを Bolt 28 で作った）。テスト戦略の「Release 0.1 の受入条件」の表の各行の「確かめるもの」のタグ（{@code @US-nn-ACm}・
 * {@code @B-INV-nn}）に、{@code @wip} でないシナリオがあることを確かめる。Feature は Cucumber の解析器で Pickle にし、機能・ルール・
 * シナリオ・例のタグを継承した結果で数える（行の手書きの解析の取りこぼしを避ける。Bolt 28 の開発レビュー）。設計文書と Feature を直接読む
 * ため、Gradle の documentationTest で動かす（入力として宣言している）。
 */
@Tag("documentation")
class AcceptanceCriteriaCoverageTest {

    private static final Path TEST_STRATEGY = Path.of(
            System.getProperty("cargotracker.test-strategy", "../../docs/design/cargo-tracker/test_strategy.md"));
    private static final Path FEATURES =
            Path.of(System.getProperty("cargotracker.features", "src/test/resources/features"));
    private static final String RELEASE_01_HEADING = "### Release 0.1 の受入条件";
    private static final String RELEASE_10_HEADING = "### Release 1.0 の受入条件の照合の表";
    // 列の前後の空白は [^|]+ に含める（\s* と [^|]+? の両方が空白に当たるとバックトラックが増える。SonarQube の指摘。
    // 2 列目は使わず、3 列目はタグを TAG で探すので、空白が残っても結果は変わらない。Bolt 29b）
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*(US-\\d+)\\s*\\|([^|]+)\\|([^|]+)\\|$");
    private static final Pattern TAG = Pattern.compile("`(@[A-Za-z0-9-]+)`");
    private static final String WIP = "@wip";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {RELEASE_01_HEADING, RELEASE_10_HEADING})
    void 照合の表の受入条件ごとにwipでないシナリオがある(String heading) throws IOException {
        // 表が読めずに空振りすると requiredTags が失敗する（T-85）
        List<String> required = requiredTags(Files.readAllLines(TEST_STRATEGY), heading);

        assertThat(missing(required, coveredTags())).as("@wip でないシナリオのない受入条件").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {RELEASE_01_HEADING, RELEASE_10_HEADING})
    void 表の行が書式から外れると黙って落とさずに失敗する(String heading) {
        List<String> lines = List.of(
                heading,
                "",
                "| ストーリー | 受入条件 | 確かめるもの |",
                "| :--- | :--- | :--- |",
                "| US-01 | AC1 | `@US-01-AC1` |",
                "| US-01 | AC2 |");

        assertThatThrownBy(() -> requiredTags(lines, heading))
                .hasMessageContaining(heading + " の表の行を読めない")
                .hasMessageContaining("| US-01 | AC2 |");
    }

    @Test
    void 見出しがないか行が0件の表は空振りで通さずに失敗する() {
        List<String> noHeading = List.of("### 別の節", "", "| US-01 | AC1 | `@US-01-AC1` |");
        List<String> noRows =
                List.of(RELEASE_10_HEADING, "", "| ストーリー | 受入条件 | 確かめるもの |", "| :--- | :--- | :--- |", "", "### 次の節");

        assertThatThrownBy(() -> requiredTags(noHeading, RELEASE_10_HEADING)).hasMessageContaining(RELEASE_10_HEADING);
        assertThatThrownBy(() -> requiredTags(noRows, RELEASE_10_HEADING)).hasMessageContaining(RELEASE_10_HEADING);
    }

    @Test
    void 行のタグが取れるのは見出しの節の表だけで次の見出しで止まる() {
        List<String> lines = List.of(
                "### 前の節",
                "| US-77 | AC1 | `@US-77-AC1` |",
                RELEASE_10_HEADING,
                "",
                "| ストーリー | 受入条件 | 確かめるもの |",
                "| :--- | :--- | :--- |",
                "| US-14 | AC1 | `@US-14-AC1` |",
                "| US-12 | AC3 | `@US-12-AC3` `@US-12-AC3b` |",
                "",
                "### 次の節",
                "| US-78 | AC1 | `@US-78-AC1` |");

        assertThat(requiredTags(lines, RELEASE_10_HEADING)).containsExactly("@US-14-AC1", "@US-12-AC3", "@US-12-AC3b");
    }

    @Test
    void 見出しは完全一致で探し前方一致する別の見出しの表を混ぜない() {
        List<String> lines = List.of(
                RELEASE_01_HEADING,
                "",
                "| ストーリー | 受入条件 | 確かめるもの |",
                "| :--- | :--- | :--- |",
                "| US-01 | AC1 | `@US-01-AC1` |",
                "",
                RELEASE_01_HEADING + "の前の対応",
                "",
                "| ストーリー | 受入条件 | Bolt | 確かめるもの |",
                "| :--- | :--- | :--- | :--- |",
                "| US-18 | AC8 | 33 | `@US-18-AC8` |");

        assertThat(requiredTags(lines, RELEASE_01_HEADING)).containsExactly("@US-01-AC1");
    }

    @Test
    void 同じ見出しの節の2つ目の表は黙って読まずに失敗する() {
        List<String> lines = List.of(
                RELEASE_10_HEADING,
                "",
                "| ストーリー | 受入条件 | 確かめるもの |",
                "| :--- | :--- | :--- |",
                "| US-14 | AC1 | `@US-14-AC1` |",
                "",
                "| ストーリー | 受入条件 | Bolt | 確かめるもの |",
                "| :--- | :--- | :--- | :--- |");

        assertThatThrownBy(() -> requiredTags(lines, RELEASE_10_HEADING))
                .hasMessageContaining("| ストーリー | 受入条件 | Bolt | 確かめるもの |");
    }

    @Test
    void 表にあるのにシナリオがない受入条件を見つける() {
        // 照合が働かなくなったことを検出する（T-88 の手作業の確かめを回帰のテストにした）
        assertThat(missing(List.of("@US-09-AC1", "@US-09-AC9"), Set.of("@US-09-AC1")))
                .containsExactly("@US-09-AC9");
    }

    @Test
    void ルールと例のタグを継承しwipの例とシナリオは数えない() {
        String feature = """
                # language: ja
                @US-99
                機能: 解析の確かめ
                  @US-99-AC1
                  ルール: 規則
                    シナリオ: ルールの中のシナリオ
                      前提 何かがある
                  @US-99-AC2
                  シナリオアウトライン: 例ごとのシナリオ
                    前提 <値> がある
                    @US-99-AC3
                    例: 通る例
                      | 値 |
                      | 1 |
                    @wip @US-99-AC4
                    例: 作業中の例
                      | 値 |
                      | 2 |
                  @wip @US-99-AC5
                  シナリオ: 作業中のシナリオ
                    前提 何かがある
                """;

        assertThat(coveredTags("parser-check.feature", feature))
                .contains("@US-99", "@US-99-AC1", "@US-99-AC2", "@US-99-AC3")
                .doesNotContain("@US-99-AC4", "@US-99-AC5", WIP);
    }

    /** テスト戦略の見出しの節の表の「確かめるもの」のタグ。表のデータの行は 1 行も読み落とさない。 */
    static List<String> requiredTags(List<String> lines, String heading) {
        List<String> tags = new ArrayList<>();
        boolean inSection = false;
        int tableLine = 0;
        for (String line : lines) {
            if (line.strip().equals(heading)) {
                inSection = true;
            } else if (inSection && line.startsWith("#")) {
                break;
            } else if (inSection && line.startsWith("|") && ++tableLine > 2) {
                // 見出しと区切りの 2 行の後はすべてデータの行
                tags.addAll(tagsOf(line, heading));
            }
        }
        if (tags.isEmpty()) {
            // 表が読めずに空振りしない（T-85）。見出しがない、または表の行が 0 件
            throw new IllegalStateException(heading + " の表の行が 1 つもない（見出しがないか、表が読めない）");
        }
        return tags;
    }

    private static List<String> tagsOf(String line, String heading) {
        Matcher row = TABLE_ROW.matcher(line.strip());
        if (!row.matches()) {
            throw new IllegalStateException(heading + " の表の行を読めない: " + line);
        }
        List<String> tags = new ArrayList<>();
        Matcher tag = TAG.matcher(row.group(3));
        while (tag.find()) {
            tags.add(tag.group(1));
        }
        return tags;
    }

    static List<String> missing(List<String> required, Set<String> covered) {
        return required.stream().filter(tag -> !covered.contains(tag)).toList();
    }

    private static Set<String> coveredTags() throws IOException {
        Set<String> covered = new HashSet<>();
        try (Stream<Path> files = Files.walk(FEATURES)) {
            for (Path feature :
                    files.filter(p -> p.toString().endsWith(".feature")).toList()) {
                covered.addAll(coveredTags(feature.toString(), Files.readString(feature, StandardCharsets.UTF_8)));
            }
        }
        return covered;
    }

    /** {@code @wip} でない Pickle（シナリオと例の行ごと）のタグ。解析の誤りは照合の失敗にする。 */
    static Set<String> coveredTags(String uri, String feature) {
        GherkinParser parser = GherkinParser.builder()
                .includeSource(false)
                .includeGherkinDocument(false)
                .build();
        List<Envelope> envelopes =
                parser.parse(uri, feature.getBytes(StandardCharsets.UTF_8)).toList();
        envelopes.stream()
                .flatMap(envelope -> envelope.getParseError().stream())
                .findFirst()
                .ifPresent(error -> {
                    throw new IllegalStateException(uri + " を解析できない: " + error.getMessage());
                });
        Set<String> covered = new HashSet<>();
        envelopes.stream()
                .flatMap(envelope -> envelope.getPickle().stream())
                .map(Pickle::getTags)
                .map(pickleTags -> pickleTags.stream().map(PickleTag::getName).toList())
                .filter(names -> !names.contains(WIP))
                .forEach(covered::addAll);
        return covered;
    }
}
