package com.example.cargotracker.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 要素の権限（FACTOR_PASSWORD・FACTOR_TOTP）の参照は、認証の入口（identity.infrastructure.security）と合成ルート
 * （identity.infrastructure.config）に閉じ、画面のテンプレートにも書かない（Bolt 31d、Bolt 31 のレビュー 17、ADR-011 のコンプライアンス）。{@code FACTOR_TOTP} は
 * コンパイル時の定数なので、ほかのコンテキストが使っても値が埋め込まれて ArchUnit（クラスの依存）では見えない。そのためソースの
 * 文字列を走査して確かめる。
 */
class FactorAuthorityReferenceTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    /** 要素の権限の名前（引用符を問わない）、要素の権限の型、TOTP の要素の定数を持つ型（Bolt 31d の開発レビューで広げた）。 */
    private static final Pattern FACTOR_REFERENCE =
            Pattern.compile("FACTOR_[A-Z]|FactorGrantedAuthority|TotpAuthenticationToken");

    private static final List<String> ALLOWED = List.of(
            "com/example/cargotracker/identity/infrastructure/security/",
            "com/example/cargotracker/identity/infrastructure/config/");

    @Test
    void 要素の権限は認証の入口と合成ルートだけが書く() throws IOException {
        List<Path> sources = files(SOURCES, ".java");
        List<String> referencing = sources.stream()
                .filter(FactorAuthorityReferenceTest::refersToFactors)
                .map(path -> SOURCES.relativize(path).toString().replace('\\', '/'))
                .toList();
        List<Path> templates = files(RESOURCES, ".html");

        // 走査が空振りしていないこと（T-107）: ソースがあり、要素の権限を定める入口のクラスが見つかる
        assertThat(sources).as("走査したソース").hasSizeGreaterThan(100);
        assertThat(referencing)
                .as("定数の参照（TotpAuthenticationToken.FACTOR_TOTP）だけのファイルも拾う")
                .contains(
                        "com/example/cargotracker/identity/infrastructure/security/TotpAuthenticationToken.java",
                        "com/example/cargotracker/identity/infrastructure/security/LoginSuccessHandler.java")
                .allMatch(path -> ALLOWED.stream().anyMatch(path::startsWith));
        // 画面のテンプレート（sec:authorize の hasAuthority('FACTOR_TOTP') など）も要素の権限を書かない
        assertThat(templates).as("走査したテンプレート").isNotEmpty().noneMatch(FactorAuthorityReferenceTest::refersToFactors);
    }

    private static List<Path> files(Path root, String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(extension)).toList();
        }
    }

    private static boolean refersToFactors(Path path) {
        try {
            return FACTOR_REFERENCE
                    .matcher(Files.readString(path, StandardCharsets.UTF_8))
                    .find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
