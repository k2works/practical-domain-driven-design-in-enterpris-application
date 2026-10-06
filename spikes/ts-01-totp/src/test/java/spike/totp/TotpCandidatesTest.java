package spike.totp;

import static org.assertj.core.api.Assertions.assertThat;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TOTP の候補を RFC 6238 の付録 B の試験ベクトル（HMAC-SHA1、8 桁、30 秒）で比べる（Bolt 13 ステップ 2）。
 * 秘密は ASCII の "12345678901234567890"（Base32 では GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ）。
 */
class TotpCandidatesTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @ParameterizedTest
    @CsvSource({
        "59, 94287082",
        "1111111109, 07081804",
        "1111111111, 14050471",
        "1234567890, 89005924",
        "2000000000, 69279037",
        "20000000000, 65353130"
    })
    void 候補a_JDKのHMACだけの実装がRFC6238の試験ベクトルを満たす(long epochSeconds, String expected) {
        JdkTotp totp = new JdkTotp(8, "HmacSHA1", Duration.ofSeconds(30));

        assertThat(totp.generate(SECRET, Instant.ofEpochSecond(epochSeconds))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"59, 94287082", "1111111109, 07081804", "20000000000, 65353130"})
    void 候補b_java_otpがRFC6238の試験ベクトルを満たす(long epochSeconds, String expected) throws Exception {
        TimeBasedOneTimePasswordGenerator generator =
                new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 8, "HmacSHA1");

        String code = generator.generateOneTimePasswordString(
                new SecretKeySpec(SECRET, "HmacSHA1"), Instant.ofEpochSecond(epochSeconds));

        assertThat(code).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"59, 94287082", "1111111109, 07081804", "20000000000, 65353130"})
    void 候補c_samstevensがRFC6238の試験ベクトルを満たす(long epochSeconds, String expected) throws Exception {
        CodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 8);

        assertThat(generator.generate(SECRET_BASE32, epochSeconds / 30)).isEqualTo(expected);
    }

    @Test
    void 候補a_前後1つの窓のコードを受け付けそれより外と誤りは受け付けない() {
        JdkTotp totp = new JdkTotp(6, "HmacSHA1", Duration.ofSeconds(30));
        Instant now = Instant.ofEpochSecond(1_800_000_015L);
        String previous = totp.generate(SECRET, now.minusSeconds(30));
        String twoBefore = totp.generate(SECRET, now.minusSeconds(60));

        assertThat(totp.verify(SECRET, totp.generate(SECRET, now), now, 1)).isEqualTo(60_000_000L);
        assertThat(totp.verify(SECRET, previous, now, 1)).isEqualTo(59_999_999L);
        assertThat(totp.verify(SECRET, twoBefore, now, 1)).isEqualTo(-1L);
        assertThat(totp.verify(SECRET, "000000", now, 1)).isEqualTo(-1L);
        assertThat(totp.generate(SECRET, now)).hasSize(6);
    }

    @ParameterizedTest
    @CsvSource({
        "1800000000, -1, true", "1800000000, 0, true", "1800000000, 1, true",
        "1800000029, -1, true", "1800000029, 0, true", "1800000029, 1, true",
        "1800000000, -2, false", "1800000000, 2, false", "1800000029, -2, false", "1800000029, 2, false"
    })
    void 候補a_刻みの開始と終了の時点で前後1つの窓だけを受け付ける(long epochSeconds, int offset, boolean accepted) {
        JdkTotp totp = new JdkTotp(6, "HmacSHA1", Duration.ofSeconds(30));
        Instant now = Instant.ofEpochSecond(epochSeconds);
        String code = totp.generate(SECRET, now.plusSeconds(30L * offset));

        assertThat(totp.verify(SECRET, code, now, 1) >= 0).isEqualTo(accepted);
    }

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037", "20000000000, 353130"})
    void 候補b_java_otpは6桁で先頭の0を保ちRFC6238の全試験ベクトルの下6桁と一致する(long epochSeconds, String expected)
            throws Exception {
        TimeBasedOneTimePasswordGenerator generator =
                new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6, "HmacSHA1");

        assertThat(generator.generateOneTimePasswordString(
                        new SecretKeySpec(SECRET, "HmacSHA1"), Instant.ofEpochSecond(epochSeconds)))
                .isEqualTo(expected);
    }
}
