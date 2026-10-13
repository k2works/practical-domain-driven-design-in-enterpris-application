package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TOTP の生成の契約（Bolt 30、RFC 6238 の付録 B の試験ベクトル、HMAC-SHA1）。刻みは epoch 秒 / 30。
 * 6 桁は ADR-011 のスパイクと同じ値（先頭の 0 を保つ）、8 桁は RFC の付録 B の値。
 */
class JavaOtpTotpCodeGeneratorTest {

    /** RFC 6238 の付録 B の SHA1 の秘密（ASCII の "12345678901234567890"）。 */
    private static final byte[] PLAIN_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static final UserId OWNER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));

    private static final AesGcmSecretCipher CIPHER = TestSecretCiphers.cipher();

    /** 生成器は暗号化した秘密を受け、中で復号する（Bolt 31、ADR-011）。 */
    private static final EncryptedSecret SECRET = CIPHER.encrypt(PLAIN_SECRET.clone(), OWNER);

    private static long step(long epochSeconds) {
        return epochSeconds / 30;
    }

    @ParameterizedTest
    @CsvSource({
        "59, 287082",
        "1111111109, 081804",
        "1111111111, 050471",
        "1234567890, 005924",
        "2000000000, 279037",
        "20000000000, 353130"
    })
    void 六桁のコードはRFC6238の試験ベクトルの下6桁と一致し先頭の0を保つ(long epochSeconds, String expected) {
        assertThat(new JavaOtpTotpCodeGenerator(CIPHER).code(OWNER, SECRET, step(epochSeconds)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "59, 94287082",
        "1111111109, 07081804",
        "1111111111, 14050471",
        "1234567890, 89005924",
        "2000000000, 69279037",
        "20000000000, 65353130"
    })
    void 八桁のコードはRFC6238の試験ベクトルと一致する(long epochSeconds, String expected) {
        assertThat(new JavaOtpTotpCodeGenerator(CIPHER, 8).code(OWNER, SECRET, step(epochSeconds)))
                .isEqualTo(expected);
    }

    @Test
    void 同じ生成器を並行に使っても試験ベクトルどおりのコードを作る() {
        JavaOtpTotpCodeGenerator generator = new JavaOtpTotpCodeGenerator(CIPHER);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Boolean>> results = IntStream.range(0, 8)
                    .mapToObj(thread -> CompletableFuture.supplyAsync(
                            () -> IntStream.range(0, 2_000)
                                    .allMatch(i -> generator
                                                    .code(OWNER, SECRET, step(59))
                                                    .equals("287082")
                                            && generator
                                                    .code(OWNER, SECRET, step(1111111111))
                                                    .equals("050471")),
                            executor))
                    .toList();

            assertThat(results.stream()
                            .map(future ->
                                    future.orTimeout(60, TimeUnit.SECONDS).join())
                            .toList())
                    .containsOnly(true);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 既定のロケールがASCIIでない数字を使ってもASCIIの数字で作る() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
            String code = new JavaOtpTotpCodeGenerator(CIPHER).code(OWNER, SECRET, step(59));

            assertThat(code).isEqualTo("287082");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void 秘密がないときと負の刻みと復号できない秘密は失敗へ倒す() {
        JavaOtpTotpCodeGenerator generator = new JavaOtpTotpCodeGenerator(CIPHER);

        assertThatThrownBy(() -> generator.code(OWNER, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.code(OWNER, SECRET, -1)).isInstanceOf(IllegalArgumentException.class);
        EncryptedSecret unknownVersion = new EncryptedSecret("v9:AAAA");
        assertThatThrownBy(() -> generator.code(OWNER, unknownVersion, 1))
                .isInstanceOf(UnreadableCredentialException.class);
    }
}
