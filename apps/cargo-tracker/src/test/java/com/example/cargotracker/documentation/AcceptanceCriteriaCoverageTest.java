package com.example.cargotracker.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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

/**
 * 受入条件とシナリオの照合（テスト戦略「CI で受入条件の数と照合して確かめる（ADR-009 のコンプライアンス）」。Bolt 2 レビュー R-17 の
 * 持ち越しを Bolt 28 で作った）。テスト戦略の「Release 0.1 の受入条件」の表の各行の「確かめるもの」のタグ（{@code @US-nn-ACm}・
 * {@code @B-INV-nn}）に、{@code @wip} でないシナリオがあることを確かめる。設計文書と Feature を直接読むため、Gradle の
 * documentationTest で動かす（入力として宣言している）。
 */
@Tag("documentation")
class AcceptanceCriteriaCoverageTest {

    private static final Path TEST_STRATEGY = Path.of(
            System.getProperty("cargotracker.test-strategy", "../../docs/design/cargo-tracker/test_strategy.md"));
    private static final Path FEATURES = Path.of("src/test/resources/features");
    private static final String RELEASE_01_HEADING = "### Release 0.1 の受入条件";
    private static final Pattern TABLE_ROW = Pattern.compile("^\\| (US-\\d+) \\| ([^|]+?) \\| ([^|]+?) \\|$");
    private static final Pattern TAG = Pattern.compile("`(@[A-Za-z0-9-]+)`");
    private static final Pattern SCENARIO = Pattern.compile("^\\s*(シナリオ|シナリオアウトライン|シナリオテンプレート):");
    private static final Pattern FEATURE = Pattern.compile("^\\s*機能:");

    @Test
    void Release01の受入条件ごとにwipでないシナリオがある() throws IOException {
        List<String> required = requiredTags();
        // 表が読めずに空振りしないこと（T-85）
        assertThat(required).as("Release 0.1 の受入条件の表の行").isNotEmpty();

        Set<String> covered = coveredTags();
        assertThat(required)
                .as("シナリオ（@wip でない）のない受入条件")
                .allSatisfy(tag -> assertThat(covered).contains(tag));
    }

    /** テスト戦略の「Release 0.1 の受入条件」の表の「確かめるもの」のタグ。 */
    private static List<String> requiredTags() throws IOException {
        List<String> tags = new ArrayList<>();
        boolean inSection = false;
        for (String line : Files.readAllLines(TEST_STRATEGY)) {
            if (line.startsWith(RELEASE_01_HEADING)) {
                inSection = true;
                continue;
            }
            if (inSection && line.startsWith("#")) {
                break;
            }
            Matcher row = TABLE_ROW.matcher(line);
            if (inSection && row.matches()) {
                Matcher tag = TAG.matcher(row.group(3));
                while (tag.find()) {
                    tags.add(tag.group(1));
                }
            }
        }
        return tags;
    }

    /** {@code @wip} でないシナリオに付いたタグ（機能のタグを含む）。 */
    private static Set<String> coveredTags() throws IOException {
        Set<String> covered = new HashSet<>();
        try (Stream<Path> files = Files.walk(FEATURES)) {
            for (Path feature :
                    files.filter(p -> p.toString().endsWith(".feature")).toList()) {
                covered.addAll(coveredTags(Files.readAllLines(feature)));
            }
        }
        return covered;
    }

    private static Set<String> coveredTags(List<String> lines) {
        Set<String> covered = new HashSet<>();
        Set<String> featureTags = new HashSet<>();
        Set<String> pending = new HashSet<>();
        for (String line : lines) {
            String text = line.strip();
            if (text.startsWith("@")) {
                for (String tag : text.split("\\s+")) {
                    pending.add(tag);
                }
            } else if (FEATURE.matcher(line).find()) {
                featureTags.addAll(pending);
                pending.clear();
            } else if (SCENARIO.matcher(line).find()) {
                Set<String> scenarioTags = new HashSet<>(featureTags);
                scenarioTags.addAll(pending);
                if (!scenarioTags.contains("@wip")) {
                    covered.addAll(scenarioTags);
                }
                pending.clear();
            } else if (!text.isEmpty() && !text.startsWith("#")) {
                pending.clear();
            }
        }
        return covered;
    }
}
