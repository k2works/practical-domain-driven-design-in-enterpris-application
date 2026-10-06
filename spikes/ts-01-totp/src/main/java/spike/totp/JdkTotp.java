package spike.totp;

import java.time.Duration;
import java.time.Instant;

/**
 * 候補 (a): JDK の HMAC だけで実装する TOTP（RFC 6238）。時間の刻みは 30 秒、桁数と HMAC の算法を選べる。
 *
 * @param digits 桁数（US-18 は 6 桁。RFC 6238 の試験ベクトルは 8 桁）
 * @param algorithm HMAC の算法（例: HmacSHA1）
 * @param step 時間の刻み
 */
public record JdkTotp(int digits, String algorithm, Duration step) {

    /** 時刻のコードを返す（桁数に満たなければ先頭を 0 で埋める）。 */
    public String generate(byte[] secret, Instant at) {
        return "";
    }

    /** 前後 window 個の時間の窓のどれかに一致すれば、一致した時間の刻みの番号を返す。一致しなければ -1。 */
    public long verify(byte[] secret, String code, Instant at, int window) {
        return -1;
    }
}
