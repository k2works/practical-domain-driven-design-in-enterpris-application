package com.example.cargotracker.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 用語集（ドメインモデル設計）とコードの整合を確かめる（開発戦略「重複させた知識の検証」）。
 * 集約ルートと値オブジェクトの英語名が用語集にあり、Javadoc の最初の語が用語集の日本語名と一致することを確かめる。
 * ドメインイベントは用語集ではなくイベントの表（DE-nn）で定義するため、ここでは対象にしない。
 */
class LivingGlossaryConsistencyTest {

    private static final Path DOMAIN_MODEL = Path.of("../../docs/design/cargo-tracker/domain_model.md");
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern GLOSSARY_ROW = Pattern.compile("^\\| ([^|]+?) \\| ([A-Za-z][A-Za-z0-9]*) \\|.*");
    private static final Pattern CLASS_JAVADOC_FIRST_LINE = Pattern.compile("/\\*\\*\\s*\\n\\s*\\*\\s*([^\\n。]+)");

    @Test
    void 集約ルートと値オブジェクトは用語集にありJavadocが用語集の日本語名で始まる() throws IOException {
        Map<String, String> glossary = readGlossary();
        List<String> mismatches = new ArrayList<>();

        for (JavaClass javaClass : documentedClasses()) {
            String english = javaClass.getSimpleName();
            String japanese = glossary.get(english);
            if (japanese == null) {
                mismatches.add(english + ": 用語集にない");
                continue;
            }
            String firstTerm = javadocFirstTerm(javaClass);
            if (!japanese.equals(firstTerm)) {
                mismatches.add(english + ": Javadoc は「" + firstTerm + "」、用語集は「" + japanese + "」");
            }
        }

        assertThat(mismatches).isEmpty();
    }

    private static List<JavaClass> documentedClasses() {
        return new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.example.cargotracker")
                        .stream()
                        .filter(c -> c.isAnnotatedWith(AggregateRoot.class) || c.isAnnotatedWith(ValueObject.class))
                        .toList();
    }

    /** 用語集の表（用語・英語名・定義・コンテキスト）を、英語名から日本語名への対応にする。 */
    private static Map<String, String> readGlossary() throws IOException {
        Map<String, String> glossary = new LinkedHashMap<>();
        boolean inGlossary = false;
        for (String line : Files.readAllLines(DOMAIN_MODEL)) {
            if (line.startsWith("### 用語集")) {
                inGlossary = true;
                continue;
            }
            if (inGlossary && line.startsWith("#")) {
                break;
            }
            Matcher row = GLOSSARY_ROW.matcher(line);
            if (inGlossary && row.matches()) {
                glossary.put(row.group(2), row.group(1).strip());
            }
        }
        assertThat(glossary).as("用語集の表を読めること").isNotEmpty();
        return glossary;
    }

    /** クラスの Javadoc の最初の語（最初の「。」まで）を返す。 */
    private static String javadocFirstTerm(JavaClass javaClass) throws IOException {
        Path source = SOURCE_ROOT.resolve(javaClass.getName().replace('.', '/') + ".java");
        String text = Files.readString(source);
        int declaration = text.indexOf(" " + javaClass.getSimpleName());
        Matcher javadoc = CLASS_JAVADOC_FIRST_LINE.matcher(text.substring(0, declaration));
        String first = null;
        while (javadoc.find()) {
            first = javadoc.group(1).strip();
        }
        return first;
    }
}
