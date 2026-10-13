package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerification;
import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * A-02 の TOTP の provider（Bolt 31、Bolt 31d）。サービスの結果を認証の成否に直す。保存した秘密を読めないときだけ拒否に倒し、
 * ほかの実装の誤りは利用者の誤りに見せずに投げる（確認ポイント 5）。
 */
class TotpAuthenticationProviderTest {

    private static final CargoUserDetails DETAILS = new CargoUserDetails(
            new AuthenticatedActor(
                    new UserId(UUID.randomUUID()),
                    new CompanyId(UUID.randomUUID()),
                    Set.of(Role.SHIPPER),
                    "荷主 太郎",
                    "荷主 A"),
            "shipper@example.com",
            true,
            null);

    /** 結果を決めた偽のサービス。 */
    private static final class StubService extends TotpVerificationService {
        private final TotpVerification result;
        private final RuntimeException failure;

        StubService(TotpVerification result, RuntimeException failure) {
            super(null, null, Clock.systemUTC());
            this.result = result;
            this.failure = failure;
        }

        @Override
        public TotpVerification verify(UserId userId, String code) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static TotpAuthenticationToken input() {
        return TotpAuthenticationToken.unauthenticated(DETAILS, "123456");
    }

    @Test
    void 通ればTOTPの要素の権限の結果を返す() {
        TotpAuthenticationProvider provider =
                new TotpAuthenticationProvider(new StubService(TotpVerification.ACCEPTED, null));

        assertThat(provider.authenticate(input()).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(TotpAuthenticationToken.FACTOR_TOTP);
    }

    @Test
    void 保存した秘密を読めないときは500にせず拒否する() {
        TotpAuthenticationProvider provider =
                new TotpAuthenticationProvider(new StubService(null, new UnreadableCredentialException("知らない鍵の版")));
        TotpAuthenticationToken given = input();

        assertThatThrownBy(() -> provider.authenticate(given)).isInstanceOf(TotpRejectedException.class);
    }

    @Test
    void 読めない要素でない実装の誤りは拒否に見せず投げる() {
        TotpAuthenticationProvider provider =
                new TotpAuthenticationProvider(new StubService(null, new IllegalArgumentException("値の検証の誤り")));
        TotpAuthenticationToken given = input();

        assertThatThrownBy(() -> provider.authenticate(given)).isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
