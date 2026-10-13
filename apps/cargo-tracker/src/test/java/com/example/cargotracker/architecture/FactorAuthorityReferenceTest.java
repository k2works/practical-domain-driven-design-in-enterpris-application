package com.example.cargotracker.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 要素の権限（FACTOR_PASSWORD・FACTOR_TOTP）の参照は、認証の入口（identity.infrastructure.security）と合成ルート
 * （identity.infrastructure.config）に閉じる（Bolt 31d、Bolt 31 のレビュー 17、ADR-011 のコンプライアンス）。{@code FACTOR_TOTP} は
 * コンパイル時の定数なので、ほかのコンテキストが使っても値が埋め込まれて ArchUnit（クラスの依存）では見えない。そのためソースの
 * 文字列を走査して確かめる。
 */
class FactorAuthorityReferenceTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final List<String> ALLOWED = List.of(
            "com/example/cargotracker/identity/infrastructure/security/",
            "com/example/cargotracker/identity/infrastructure/config/");

    @Test
    void 要素の権限は認証の入口と合成ルートだけが書く() throws IOException {
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
        List<String> referencing = sources.stream()
                .filter(FactorAuthorityReferenceTest::refersToFactors)
                .map(path -> SOURCES.relativize(path).toString().replace('\\', '/'))
                .toList();

        // 走査が空振りしていないこと（T-107）: ソースがあり、要素の権限を定める入口のクラスが見つかる
        assertThat(sources).as("走査したソース").hasSizeGreaterThan(100);
        assertThat(referencing)
                .contains("com/example/cargotracker/identity/infrastructure/security/TotpAuthenticationToken.java");
        assertThat(referencing).allMatch(path -> ALLOWED.stream().anyMatch(path::startsWith));
    }

    private static boolean refersToFactors(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains("\"FACTOR_") || source.contains("FactorGrantedAuthority");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
