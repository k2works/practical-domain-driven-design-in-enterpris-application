package spike.totp;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

/**
 * スパイクの利用者（荷主担当者 1 人）。本体では利用者・TOTP の秘密・回復コード・使ったコード・失敗の回数を表に持つ（ADR 011 の案）。
 * ここではメモリに持ち、TOTP の検証の外で、再利用の拒否（H3）と、password と TOTP を通した連続の失敗によるロックを守る。
 */
@Component
public class SpikeUsers implements UserDetailsService {

    public static final String SHIPPER = "shipper@example.com";
    public static final String PASSWORD = "correct horse battery staple";

    /** 開発用の固定の TOTP の秘密（スパイクと開発環境だけ。本体では利用者ごとに生成して暗号化して保存する）。 */
    private static final byte[] DEV_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final int WINDOW = 1;

    private final JdkTotp totp = new JdkTotp(6, "HmacSHA1", Duration.ofSeconds(30));
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
        long step = totp.verify(DEV_SECRET, code, clock.instant(), WINDOW);
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
        return totp.generate(DEV_SECRET, at);
    }

    public List<String> recoveryCodes(String username) {
        return List.copyOf(states.get(username).recoveryCodes);
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
