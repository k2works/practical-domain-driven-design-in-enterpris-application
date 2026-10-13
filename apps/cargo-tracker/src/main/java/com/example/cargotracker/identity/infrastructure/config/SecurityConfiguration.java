package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.infrastructure.security.AuthenticationAuditListener;
import com.example.cargotracker.identity.infrastructure.security.CargoUserDetailsService;
import com.example.cargotracker.identity.infrastructure.security.KeepEmailAuthenticationFailureHandler;
import com.example.cargotracker.identity.infrastructure.security.LockAwareUserDetailsChecker;
import com.example.cargotracker.identity.infrastructure.security.LoginSuccessHandler;
import com.example.cargotracker.identity.infrastructure.security.RecoveryCodeAuthenticationProvider;
import com.example.cargotracker.identity.infrastructure.security.RecoveryCodeRejectedException;
import com.example.cargotracker.identity.infrastructure.security.SessionLifetime;
import com.example.cargotracker.identity.infrastructure.security.SessionLifetimeFilter;
import com.example.cargotracker.identity.infrastructure.security.TotpAuthenticationFilter;
import com.example.cargotracker.identity.infrastructure.security.TotpAuthenticationProvider;
import com.example.cargotracker.identity.infrastructure.security.TotpAuthenticationToken;
import com.example.cargotracker.identity.infrastructure.security.TotpRejectedException;
import com.example.cargotracker.shared.domain.Role;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.DelegatingMissingAuthorityAccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 認証と認可の構成（US-18 の password の段と TOTP の段、ADR-011・ADR-012）。
 *
 * <ul>
 *   <li>form login（A-01 {@code /login}）。利用停止・無効な企業は password の照合の後に拒否し、どの失敗も同じ応答にする（AC2・AC4）
 *   <li>2 段階のログイン（Bolt 31）。password（{@code FACTOR_PASSWORD}）と TOTP（{@code FACTOR_TOTP}）の 2 つの要素を、全体の注釈で
 *       認証が要るすべての URL に求める（Bolt 13 レビューの R-08 の比較で全体の注釈を採った）。password の要素だけで開けるのは
 *       A-02（{@code /login/totp}）と登録の案内（{@code /login/totp/enroll}）だけ。足りない要素の画面へ導く
 *   <li>荷主の画面（{@code /customer/**}）は荷主担当者、社内の画面（{@code /staff/**}）は営業担当者。経路設計（{@code /staff/routing-cases/**}）は
 *       経路設計者、追跡（{@code /staff/tracking-records/**}）は追跡管理者。ログインの時点の役割で判定する
 *       （request ごとに DB で確かめるのは W5。ADR-012）
 *   <li>session の固定化の防止（ログインで session ID を付け替える。既定）、CSRF の同期トークン（既定）
 *   <li>無操作 30 分（Spring Session の期限）と、TOTP が初めて通った時刻から 8 時間・password の段だけは 5 分
 *       （{@link SessionLifetimeFilter}）。失効したら A-03
 *   <li>応答のヘッダー（SEC-17）
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMultiFactorAuthentication(
        authorities = {FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP})
public class SecurityConfiguration {

    /** 認証なしで開ける URL（ログイン、再認証の案内、エラー、静的な資源）。 */
    /** A-01 の URL。 */
    private static final String LOGIN_URL = "/login";

    private static final String[] PUBLIC_PATHS = {
        LOGIN_URL, SessionLifetime.EXPIRED_URL, "/error", "/css/**", "/js/**", "/webjars/**", "/favicon.ico"
    };

    /**
     * 外部の資源とインラインのスクリプトを読まない（SEC-17）。default-src が覆わない埋め込み・フォームの送信先・base も閉じる
     * （Bolt 14 レビュー）。
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CargoUserDetailsService cargoUserDetailsService(UserRepository users, CompanyRepository companies) {
        return new CargoUserDetailsService(users, companies);
    }

    @Bean
    AuthenticationAuditListener authenticationAuditListener(
            AuditRecordRepository auditRecords,
            CargoUserDetailsService userDetails,
            CompanyRepository companies,
            Clock clock,
            LoginAttemptService loginAttemptService) {
        return new AuthenticationAuditListener(auditRecords, userDetails, companies, clock, loginAttemptService);
    }

    /**
     * password を先に照合し、利用停止・無効な企業はその後に拒否する。照合の前に拒否すると、誤った password でも
     * 利用停止であることが応答の時間から分かってしまう（AC2）。
     */
    @Bean
    AuthenticationManager authenticationManager(
            CargoUserDetailsService userDetails,
            PasswordEncoder passwordEncoder,
            TotpVerificationService totpVerificationService,
            RecoveryCodeVerificationService recoveryCodeVerificationService,
            LoginAttemptService loginAttemptService,
            ApplicationEventPublisher publisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetails);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> {});
        // ロックは照合の後に 1 か所で確かめる（Bolt 32、AC2）。続けて利用停止・無効な企業を確かめる
        provider.setPostAuthenticationChecks(
                new LockAwareUserDetailsChecker(loginAttemptService, new AccountStatusUserDetailsChecker()));
        // TOTP の要素も同じ認証のイベント（成功・失敗）で監査記録に書く（ADR-011 の決定 2）
        ProviderManager manager = new ProviderManager(
                provider,
                new TotpAuthenticationProvider(totpVerificationService, loginAttemptService),
                new RecoveryCodeAuthenticationProvider(recoveryCodeVerificationService, loginAttemptService));
        DefaultAuthenticationEventPublisher events = new DefaultAuthenticationEventPublisher(publisher);
        // 失敗のイベントは例外のクラス名の完全一致で選ばれる。BadCredentialsException を継いだ TOTP の拒否も、password の誤りと
        // 同じ失敗のイベントにする（対応がないとイベントが出ず、監査記録に残らない。Bolt 31 のステップ 5 で見つけた）
        events.setAdditionalExceptionMappings(Map.of(
                TotpRejectedException.class,
                AuthenticationFailureBadCredentialsEvent.class,
                RecoveryCodeRejectedException.class,
                AuthenticationFailureBadCredentialsEvent.class,
                // 利用者の読み出しの DB の誤り（DaoAuthenticationProvider が包む）。既定の対応は親の型だけなので足す（Bolt 32）
                InternalAuthenticationServiceException.class,
                AuthenticationFailureServiceExceptionEvent.class));
        manager.setAuthenticationEventPublisher(events);
        return manager;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            TotpVerificationService totpVerificationService,
            Clock clock) {
        return http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // A-02 と登録の案内は password の要素だけで開ける。access に渡した判定は全体の注釈の 2 つの要素の要求を
                        // 足されない（ほかの URL は既定で 2 つの要素を求めるので、新しい URL を足しても要求が漏れない）
                        .requestMatchers(TotpAuthenticationFilter.URL, TotpAuthenticationFilter.ENROLL_URL)
                        .access(AuthorityAuthorizationManager.hasAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY))
                        .requestMatchers("/customer/**")
                        .hasRole(Role.SHIPPER.name())
                        // 経路設計の画面は経路設計者だけ。ほかの社内の画面より先に書く（Bolt 17 計画の確認ポイント 11）
                        .requestMatchers("/staff/routing-cases/**")
                        .hasRole(Role.ROUTE_DESIGNER.name())
                        // 追跡の画面は追跡管理者だけ（Bolt 26 計画の確認ポイント 4。カスタマーサポートの照会は W8）
                        .requestMatchers("/staff/tracking-records/**")
                        .hasRole(Role.TRACKING_MANAGER.name())
                        .requestMatchers("/staff/**")
                        .hasRole(Role.SALES.name())
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage(LOGIN_URL)
                        .successHandler(new LoginSuccessHandler(clock))
                        .failureHandler(new KeepEmailAuthenticationFailureHandler()))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .sessionManagement(session -> session.invalidSessionUrl(SessionLifetime.EXPIRED_URL))
                // TOTP の段は password の段の後に置く。期限の確認は両方より前に置く（期限切れの session に TOTP を送って
                // 延ばさせない。ADR-011、T-40）
                .addFilterAfter(
                        new TotpAuthenticationFilter(authenticationManager, clock),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionLifetimeFilter(clock), UsernamePasswordAuthenticationFilter.class)
                // 足りない要素の画面へ導く。TOTP が足りなければ A-02、password が足りなければ A-01
                .exceptionHandling(exceptions ->
                        exceptions.accessDeniedHandler(DelegatingMissingAuthorityAccessDeniedHandler.builder()
                                .addEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint(TotpAuthenticationFilter.URL),
                                        TotpAuthenticationToken.FACTOR_TOTP)
                                .addEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint(LOGIN_URL),
                                        FactorGrantedAuthority.PASSWORD_AUTHORITY)
                                .build()))
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .build();
    }
}
