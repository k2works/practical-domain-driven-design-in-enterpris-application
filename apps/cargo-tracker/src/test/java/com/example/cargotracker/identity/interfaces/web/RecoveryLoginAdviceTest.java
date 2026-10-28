package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/** 回復コードでログインした印の知らせの advice（Bolt 38d2）。全画面に効くので、印のない request では回復コードを読まない。 */
class RecoveryLoginAdviceTest {

    private static final AuthenticatedActor SHIPPER = new AuthenticatedActor(
            new UserId(UUID.fromString("0b8f5c2e-6f0a-4d7e-9d0c-3a1f2b4c5d6e")),
            new CompanyId(UUID.fromString("1c9a6d3f-7a1b-4e8f-8e1d-4b2a3c5d6e7f")),
            Set.of(Role.SHIPPER),
            "荷主 花子",
            "荷主 A");

    @Test
    void 印のないrequestでは回復コードを照会しない() {
        // 決定 (5) と確認ポイント 3（読む回数）。照会を呼んで結果を捨てる形も、全利用者の全 request で SELECT を流すので拒む
        TotpEnrollmentQueryService query = mock(TotpEnrollmentQueryService.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("totpEnrollmentQueryService", query);
        RecoveryLoginAdvice advice = new RecoveryLoginAdvice(
                beans.getBeanProvider(Clock.class), beans.getBeanProvider(TotpEnrollmentQueryService.class));

        assertThat(advice.remainingRecoveryCodes(new MockHttpServletRequest(), Optional.of(SHIPPER)))
                .isNull();
        verifyNoInteractions(query);
    }
}
