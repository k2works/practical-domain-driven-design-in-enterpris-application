package com.example.cargotracker.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AT-04: 各コンテキストのマッパーの SQL は自分のスキーマ以外を参照しない（ADR-001、データモデル「スキーマの所有」。Bolt 25）。
 * {@code platform} はフレームワークが定める表だけを置き、業務のコードは直接読み書きしない（データモデル）ので、許可する表はない。
 * マイグレーションが写しの列を 1 回だけ埋めるために他のスキーマを読む例外（ADR-001）は、マッパーには広げない。
 */
class MapperSchemaArchitectureTest {

    private static final Path MAPPER_ROOT = Path.of("src/main/resources/com/example/cargotracker");

    /** 業務のスキーマと基盤のスキーマ。表の参照の「スキーマ.表」の形のうち、これらの名前だけを見る（別名の「q.status」などを拾わない）。 */
    private static final Set<String> SCHEMAS =
            Set.of("quotation", "routing", "identity", "booking", "tracking", "platform");

    /** 表を参照する句（FROM・JOIN・INTO・UPDATE）の後の「スキーマ.表」。 */
    private static final Pattern TABLE_REFERENCE =
            Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-z_]+)\\.([a-z_]+)\\b", Pattern.CASE_INSENSITIVE);

    @Test
    void マッパーのSQLは自分のスキーマだけを参照する() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> mappers;
        try (Stream<Path> files = Files.walk(MAPPER_ROOT)) {
            mappers = files.filter(file -> file.getFileName().toString().endsWith("Mapper.xml"))
                    .toList();
        }
        for (Path mapper : mappers) {
            String context = MAPPER_ROOT.relativize(mapper).getName(0).toString();
            violations.addAll(violations(context, Files.readString(mapper, StandardCharsets.UTF_8)).stream()
                    .map(violation -> MAPPER_ROOT.relativize(mapper) + ": " + violation)
                    .toList());
        }

        assertThat(mappers).as("マッパーの XML が見つかること（置き場所が変わったらこのテストを直す）").isNotEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    void 他のコンテキストのスキーマを参照するSQLを見つける() {
        String sql = """
                SELECT b.id FROM booking.booking b
                  JOIN quotation.quotation q ON q.id = b.quotation_id
                """;

        assertThat(violations("booking", sql)).containsExactly("quotation.quotation");
    }

    @Test
    void 基盤のスキーマを参照するSQLを見つける() {
        assertThat(violations("tracking", "insert into platform.event_publication (id) values (#{id})"))
                .containsExactly("platform.event_publication");
    }

    @Test
    void 自分のスキーマと別名の参照は見つけない() {
        String sql = """
                UPDATE tracking.tracking_record SET version = #{version}
                 WHERE tracking_number = (SELECT r.tracking_number FROM tracking.tracking_record r)
                """;

        assertThat(violations("tracking", sql)).isEmpty();
    }

    private static List<String> violations(String context, String sql) {
        List<String> found = new ArrayList<>();
        Matcher matcher = TABLE_REFERENCE.matcher(sql);
        while (matcher.find()) {
            String schema = matcher.group(1).toLowerCase(Locale.ROOT);
            if (SCHEMAS.contains(schema) && !schema.equals(context)) {
                found.add(schema + "." + matcher.group(2).toLowerCase(Locale.ROOT));
            }
        }
        return found;
    }
}
