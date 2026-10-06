package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.Role;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * ログインの後の行き先（UI 設計「A-01」）。ログインの前に開いた画面があればそこへ、なければ役割のホームへ移す
 * （荷主担当者は C-02、営業担当者は S-02）。発行から 8 時間の上限のため、ログインの時刻を session に置く。
 */
public class RoleHomeAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    /** 荷主担当者のホーム（C-02）。 */
    public static final String CUSTOMER_HOME = "/customer/transport-requests";

    /** 営業担当者のホーム（S-02）。 */
    public static final String STAFF_HOME = "/staff/transport-requests";

    private final Clock clock;

    public RoleHomeAuthenticationSuccessHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        request.getSession().setAttribute(SessionLifetime.AUTHENTICATED_AT, clock.instant());
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** ログインの前に開いた画面がないときの行き先。部品は共有されるため、既定の行き先の設定を書き換えずに求める。 */
    @Override
    protected String determineTargetUrl(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 親は認証を null にできると宣言している。ログインの成功の後では null にならないが、null なら入口へ移す
        return authentication == null ? "/" : homeOf(authentication);
    }

    /** 役割のホーム。荷主担当者は顧客 Web、それ以外は社内業務 Web。 */
    public static String homeOf(Authentication authentication) {
        return authentication.getPrincipal() instanceof CargoUserDetails details
                        && details.actor().hasRole(Role.SHIPPER)
                ? CUSTOMER_HOME
                : STAFF_HOME;
    }
}
