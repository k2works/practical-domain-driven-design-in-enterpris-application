package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 再設定のトークンの生成とハッシュ（SEC-08。Bolt 33）。 */
class PasswordResetTokenCryptoTest {

    @Test
    void トークンは32バイトの乱数をURL安全なBase64にした43文字で毎回違う() {
        RandomPasswordResetTokenGenerator generator = new RandomPasswordResetTokenGenerator();
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(100).allSatisfy(token -> assertThat(token).matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void ハッシュはSHA256の16進の小文字64文字() {
        // FIPS 180-2 の例（"abc"）
        assertThat(new Sha256PasswordResetTokenHasher().hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
