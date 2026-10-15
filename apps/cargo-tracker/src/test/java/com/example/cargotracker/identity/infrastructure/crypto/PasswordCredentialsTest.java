package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** よく使われる password の一覧と password のハッシュ（SEC-05・SEC-07。Bolt 33a）。 */
class PasswordCredentialsTest {

    @Test
    void 同梱の一覧は大文字小文字を区別せずに照合し1文字違いは載っていない() {
        BundledCommonPasswordList list = new BundledCommonPasswordList();

        assertThat(list.size()).as("同梱の一覧を読めている（空振りしない）").isGreaterThan(40_000);
        assertThat(list.contains("password1234")).isTrue();
        assertThat(list.contains("PassWord1234")).isTrue();
        assertThat(list.contains("password1234x")).isFalse();
        assertThat(list.contains(null)).isFalse();
    }

    @Test
    void 同梱の一覧は12文字以上だけを持つ() throws Exception {
        String content =
                new ClassPathResource(BundledCommonPasswordList.LOCATION).getContentAsString(StandardCharsets.UTF_8);

        assertThat(content.lines())
                .allSatisfy(line ->
                        assertThat(line.codePointCount(0, line.length())).isBetween(12, 64));
    }

    @Test
    void 一覧が読めないか空なら作れない() {
        ClassPathResource missing = new ClassPathResource("security/missing.txt");
        ByteArrayResource empty = new ByteArrayResource(new byte[0]);

        assertThatThrownBy(() -> new BundledCommonPasswordList(missing)).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> new BundledCommonPasswordList(empty)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordのハッシュはログインと同じ形で照合できる() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        String hash = new BcryptPasswordHasher(encoder).hash("correct horse battery");

        assertThat(hash).startsWith("{bcrypt}");
        assertThat(encoder.matches("correct horse battery", hash)).isTrue();
        assertThat(encoder.matches("correct horse batterx", hash)).isFalse();
    }
}
