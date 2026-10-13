package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

/**
 * password の照合の後の確認（Bolt 32、US-18 AC3）。ロックを先に確かめ、ロックでなければ今の利用停止・無効な企業の確認に回す。
 */
class LockAwareUserDetailsCheckerTest {

    private static CargoUserDetails details(boolean enabled) {
        return new CargoUserDetails(
                new AuthenticatedActor(
                        new UserId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        Set.of(Role.SHIPPER),
                        "荷主 太郎",
                        "荷主 A"),
                "shipper@example.com",
                enabled,
                null);
    }

    private static LockAwareUserDetailsChecker checker(boolean locked) {
        return new LockAwareUserDetailsChecker(
                new FakeLoginAttemptService(locked), new AccountStatusUserDetailsChecker());
    }

    @Test
    void ロック中ならロックの例外で拒む() {
        CargoUserDetails active = details(true);

        assertThatThrownBy(() -> checker(true).check(active)).isExactlyInstanceOf(LockedException.class);
    }

    @Test
    void ロック中で利用停止でもロックを先にする() {
        CargoUserDetails disabled = details(false);

        assertThatThrownBy(() -> checker(true).check(disabled)).isExactlyInstanceOf(LockedException.class);
    }

    @Test
    void ロックでなければ今の利用停止の確認に回す() {
        CargoUserDetails disabled = details(false);

        assertThatThrownBy(() -> checker(false).check(disabled)).isExactlyInstanceOf(DisabledException.class);
    }

    @Test
    void ロックでなく有効なら通す() {
        CargoUserDetails active = details(true);

        assertThatCode(() -> checker(false).check(active)).doesNotThrowAnyException();
    }
}
