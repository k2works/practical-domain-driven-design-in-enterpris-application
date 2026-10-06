package spike.totp;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

/**
 * スパイクの利用者（荷主担当者と、取り違えの確認に使う社内の担当者）。本体では利用者・TOTP の秘密・回復コード・使ったコード・失敗の回数を表に持つ（ADR 011 の案）。
 * ここではメモリに持ち、TOTP の検証の外で、再利用の拒否（H3）と、password と TOTP を通した連続の失敗によるロックを守る。
 * コードの生成は ADR 011 の案の候補 (b) java-otp に任せ、前後の窓と一定時間の比較はこちらで行う（java-otp は検証を持たない）。
 */
@Component
public class SpikeUsers implements UserDetailsService {

    public static final String SHIPPER = "shipper@example.com";
    public static final String STAFF = "staff@example.com";
    public static final String PASSWORD = "correct horse battery staple";

    /** 開発用の固定の TOTP の秘密（スパイクと開発環境だけ。本体では利用者ごとに生成して暗号化して保存する）。 */
    private static final SecretKey DEV_SECRET =
            new SecretKeySpec("12345678901234567890".getBytes(StandardCharsets.US_ASCII), "HmacSHA1");
    private static final Duration STEP = Duration.ofSeconds(30);

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final int WINDOW = 1;

    private final TimeBasedOneTimePasswordGenerator totp = generator();
    private final String encodedPassword = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD);
    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public SpikeUsers(Clock clock) {
        this.clock = clock;
        reset();
    }

    /** 利用者の状態を初めに戻す（テストだけで使う）。 */
    public final void reset() {
        states.clear();
        states.put(SHIPPER, new State(new ArrayList<>(List.of("RC-1111-2222", "RC-3333-4444"))));
        states.put(STAFF, new State(new ArrayList<>()));
    }

    /** password の認証が使う。ロック中なら「ロックされている」として DaoAuthenticationProvider が拒否する。 */
    @Override
    public UserDetails loadUserByUsername(String username) {
        if (!states.containsKey(username)) {
            throw new UsernameNotFoundException(username);
        }
        return User.withUsername(username)
                .password(encodedPassword)
                .accountLocked(isLocked(username))
                .build();
    }

    /**
     * 2 つ目の要素を確かめる。TOTP（前後 1 つの窓、同じ時間の刻みは一度だけ）か、未使用の回復コード（一度だけ）を受け付ける。
     *
     * @return 受け付けたら true
     */
    public synchronized boolean verifySecondFactor(String username, String code) {
        State state = states.get(username);
        if (state == null || isLocked(username)) {
            return false;
        }
        long step = matchingStep(code, clock.instant());
        if (step >= 0 && step > state.lastUsedStep) {
            state.lastUsedStep = step;
            return true;
        }
        return state.recoveryCodes.remove(code);
    }

    /** password か TOTP の失敗を数える。5 回続いたら 15 分ロックする。 */
    public synchronized void recordFailure(String username) {
        State state = states.get(username);
        if (state != null && ++state.failures >= MAX_FAILURES) {
            state.lockedUntil = clock.instant().plus(LOCK);
            state.failures = 0;
        }
    }

    /** 2 つの要素がそろったら、連続の失敗を数え直す。 */
    public synchronized void recordSuccess(String username) {
        State state = states.get(username);
        if (state != null) {
            state.failures = 0;
        }
    }

    public boolean isLocked(String username) {
        State state = states.get(username);
        return state != null && state.lockedUntil != null && clock.instant().isBefore(state.lockedUntil);
    }

    /** その時点の TOTP のコード（テストと、開発環境の入力済みだけで使う）。 */
    public String currentCode(String username, Instant at) {
        return generate(at.getEpochSecond() / STEP.toSeconds());
    }

    /** 前後 WINDOW 個の時間の刻みのどれかに一致すれば、その刻みの番号を返す。一致しなければ -1。 */
    private long matchingStep(String code, Instant at) {
        long current = at.getEpochSecond() / STEP.toSeconds();
        for (long candidate = current - WINDOW; candidate <= current + WINDOW; candidate++) {
            if (MessageDigest.isEqual(
                    generate(candidate).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return candidate;
            }
        }
        return -1;
    }

    private String generate(long step) {
        try {
            return totp.generateOneTimePasswordString(DEV_SECRET, Instant.ofEpochSecond(step * STEP.toSeconds()));
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("TOTP の秘密を使えない", e);
        }
    }

    public List<String> recoveryCodes(String username) {
        return List.copyOf(states.get(username).recoveryCodes);
    }

    private static TimeBasedOneTimePasswordGenerator generator() {
        try {
            return new TimeBasedOneTimePasswordGenerator(STEP, 6, "HmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA1 を使えない", e);
        }
    }

    private static final class State {
        private final List<String> recoveryCodes;
        private long lastUsedStep = Long.MIN_VALUE;
        private int failures;
        private Instant lockedUntil;

        State(List<String> recoveryCodes) {
            this.recoveryCodes = recoveryCodes;
        }
    }
}
