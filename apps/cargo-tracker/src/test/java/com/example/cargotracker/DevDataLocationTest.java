package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 開発用の企業と利用者は、dev のときだけの Flyway の場所（db/dev-data）に置く（ADR-011 の決定 3 の 4 層目、ADR-012）。
 * 共通・ベンダーの場所に置くと、ステージング・本番にも開発用の利用者ができてしまう。
 */
class DevDataLocationTest {

    private static final Path DB = Path.of("src/main/resources/db");

    @Test
    void 共通とベンダーのマイグレーションは利用者を入れない() throws IOException {
        try (Stream<Path> files = Files.walk(DB.resolve("migration"))) {
            List<Path> seeding = files.filter(Files::isRegularFile)
                    .filter(file ->
                            read(file).toLowerCase(java.util.Locale.ROOT).contains("insert into identity.app_user"))
                    .toList();
            assertThat(seeding).isEmpty();
        }
    }

    @Test
    void 開発用の利用者はdevだけの場所に置く() throws IOException {
        try (Stream<Path> files = Files.walk(DB.resolve("dev-data"))) {
            assertThat(files.filter(Files::isRegularFile).map(DevDataLocationTest::read))
                    .anySatisfy(sql -> assertThat(sql.toLowerCase(java.util.Locale.ROOT))
                            .contains("insert into identity.app_user"));
        }
    }

    @Test
    void 既定の設定のFlywayの場所に開発用のデータを含めない() throws IOException {
        assertThat(properties("application.properties").getProperty("spring.flyway.locations"))
                .doesNotContain("dev-data");
    }

    @Test
    void devの設定のFlywayの場所に開発用のデータを含める() throws IOException {
        assertThat(properties("application-dev.properties").getProperty("spring.flyway.locations"))
                .contains("classpath:db/dev-data");
    }

    private static Properties properties(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources", name))) {
            properties.load(in);
        }
        return properties;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
