package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.shared.domain.UserId;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * password の照合の後の確認（Bolt 32、US-18 AC3、ADR-011）。まずロックを {@link LoginAttemptService#isLocked} の 1 か所で確かめて
 * {@link LockedException} を投げ、次に今の利用停止・無効な企業の確認に回す（理由の優先はロックが先）。照合の後に確かめるので、
 * 誤った password とロック中の正しい password の応答の時間が変わらない（AC2。Bolt 14 の利用停止と同じ理由）。ロックの状態は
 * 主体（session に直列化する {@code CargoUserDetails}）に写さない。ロックの読み出しの DB の誤りは、照合の後の確認なので枠組みが
 * 包まず 500 になる（利用者の読み出しの誤りが {@code /login?error} になるのとは違う。ADR-011 の決定 9）。
 */
public class LockAwareUserDetailsChecker implements UserDetailsChecker {

    private final LoginAttemptService attempts;
    private final UserDetailsChecker accountStatus;

    public LockAwareUserDetailsChecker(LoginAttemptService attempts, UserDetailsChecker accountStatus) {
        this.attempts = attempts;
        this.accountStatus = accountStatus;
    }

    @Override
    public void check(UserDetails user) {
        if (user instanceof CargoUserDetails details) {
            rejectIfLocked(attempts, details.actor().userId());
        }
        accountStatus.check(user);
    }

    /**
     * ロック中なら確かめずに {@link LockedException} で拒む。入口の 3 か所（この確認、TOTP・回復コードの provider）が同じ判定と
     * 例外を使う（失敗のイベントはロックのイベントになり、数えない）。
     */
    static void rejectIfLocked(LoginAttemptService attempts, UserId userId) {
        if (attempts.isLocked(userId)) {
            throw new LockedException("ロック中");
        }
    }
}
