package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerification;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * A-02 の回復コードの provider（Bolt 31c、ADR-011 の決定 7）。サービスの結果を認証の成否に直す。通れば TOTP と同じ結果（要素の
 * 権限 FACTOR_TOTP）、拒否は RecoveryCodeRejectedException。照合の途中の例外は 500 にせず拒否する（T-42）。
 */
class RecoveryCodeAuthenticationProviderTest {

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

    /** 結果を決めた偽のサービス。呼ばれた回数を数える。 */
    private static final class StubService extends RecoveryCodeVerificationService {
        private final RecoveryCodeVerification result;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();

        StubService(RecoveryCodeVerification result, RuntimeException failure) {
            super(null, null, null, Clock.systemUTC(), null);
            this.result = result;
            this.failure = failure;
        }

        @Override
        public RecoveryCodeVerification verify(UserId userId, CompanyId companyId, String code) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    @Test
    void 通ればTOTPと同じ要素の権限の結果を返す() {
        RecoveryCodeAuthenticationProvider provider =
                new RecoveryCodeAuthenticationProvider(new StubService(RecoveryCodeVerification.ACCEPTED, null));

        Authentication result =
                provider.authenticate(RecoveryCodeAuthenticationToken.unauthenticated(DETAILS, "1a2b-3c4d", false));

        assertThat(result).isInstanceOf(TotpAuthenticationToken.class);
        assertThat(result.getPrincipal()).isSameAs(DETAILS);
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(TotpAuthenticationToken.FACTOR_TOTP);
    }

    @Test
    void 拒否は回復コードの拒否の例外にする() {
        RecoveryCodeAuthenticationProvider provider =
                new RecoveryCodeAuthenticationProvider(new StubService(RecoveryCodeVerification.REJECTED, null));
        RecoveryCodeAuthenticationToken input =
                RecoveryCodeAuthenticationToken.unauthenticated(DETAILS, "1a2b-3c4d", false);

        assertThatThrownBy(() -> provider.authenticate(input)).isInstanceOf(RecoveryCodeRejectedException.class);
    }

    @Test
    void 認証コードも一緒に送った入力は照合せずに拒む() {
        StubService service = new StubService(RecoveryCodeVerification.ACCEPTED, null);
        RecoveryCodeAuthenticationProvider provider = new RecoveryCodeAuthenticationProvider(service);
        RecoveryCodeAuthenticationToken input =
                RecoveryCodeAuthenticationToken.unauthenticated(DETAILS, "1a2b-3c4d", true);

        assertThatThrownBy(() -> provider.authenticate(input)).isInstanceOf(RecoveryCodeRejectedException.class);
        assertThat(service.calls).hasValue(0);
    }

    @Test
    void 照合の途中の形の誤りは500にせず拒否する() {
        StubService service = new StubService(null, new IllegalArgumentException("保存したハッシュの形が違う"));
        RecoveryCodeAuthenticationProvider provider = new RecoveryCodeAuthenticationProvider(service);
        RecoveryCodeAuthenticationToken input =
                RecoveryCodeAuthenticationToken.unauthenticated(DETAILS, "1a2b-3c4d", false);

        assertThatThrownBy(() -> provider.authenticate(input)).isInstanceOf(RecoveryCodeRejectedException.class);
        assertThat(service.calls).hasValue(1);
    }

    @Test
    void passwordの段の主体がない入力は受け付けない() {
        RecoveryCodeAuthenticationProvider provider =
                new RecoveryCodeAuthenticationProvider(new StubService(RecoveryCodeVerification.ACCEPTED, null));
        RecoveryCodeAuthenticationToken input =
                RecoveryCodeAuthenticationToken.unauthenticated("anonymous", "1a2b-3c4d", false);

        assertThatThrownBy(() -> provider.authenticate(input)).isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void 回復コードの入力だけを受け持つ() {
        RecoveryCodeAuthenticationProvider provider =
                new RecoveryCodeAuthenticationProvider(new StubService(RecoveryCodeVerification.ACCEPTED, null));

        assertThat(provider.supports(RecoveryCodeAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(TotpAuthenticationToken.class)).isFalse();
    }
}
