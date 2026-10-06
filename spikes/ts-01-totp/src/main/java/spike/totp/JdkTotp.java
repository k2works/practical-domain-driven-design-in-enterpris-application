package spike.totp;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 候補 (a): JDK の HMAC だけで実装する TOTP（RFC 6238。中身は RFC 4226 の HOTP に時間の刻みの番号を渡す）。
 *
 * @param digits 桁数（US-18 は 6 桁。RFC 6238 の試験ベクトルは 8 桁）
 * @param algorithm HMAC の算法（例: HmacSHA1）
 * @param step 時間の刻み
 */
public record JdkTotp(int digits, String algorithm, Duration step) {

    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    /** 時刻のコードを返す（桁数に満たなければ先頭を 0 で埋める）。 */
    public String generate(byte[] secret, Instant at) {
        return hotp(secret, counter(at));
    }

    /**
     * 前後 window 個の時間の窓のどれかに一致すれば、一致した時間の刻みの番号を返す。一致しなければ -1。
     * 比べるのは一定時間の比較（{@link MessageDigest#isEqual}）で行う。番号は再利用の拒否に使う。
     */
    public long verify(byte[] secret, String code, Instant at, int window) {
        long current = counter(at);
        for (long candidate = current - window; candidate <= current + window; candidate++) {
            if (MessageDigest.isEqual(hotp(secret, candidate).getBytes(), code.getBytes())) {
                return candidate;
            }
        }
        return -1;
    }

    private long counter(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), step.toSeconds());
    }

    private String hotp(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret, algorithm));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            String code = Integer.toString(binary % POWERS[digits]);
            return "0".repeat(digits - code.length()) + code;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC を作れない: " + algorithm, e);
        }
    }
}
