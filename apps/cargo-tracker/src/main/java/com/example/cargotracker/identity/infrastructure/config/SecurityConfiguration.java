package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.infrastructure.security.AuthenticationAuditListener;
import com.example.cargotracker.identity.infrastructure.security.CargoUserDetailsService;
import com.example.cargotracker.identity.infrastructure.security.RoleHomeAuthenticationSuccessHandler;
import com.example.cargotracker.identity.infrastructure.security.SessionLifetime;
import com.example.cargotracker.identity.infrastructure.security.SessionLifetimeFilter;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 認証と認可の構成（US-18 の password の段、ADR-012）。
 *
 * <ul>
 *   <li>form login（A-01 {@code /login}）。利用停止・無効な企業は password の照合の後に拒否し、どの失敗も同じ応答にする（AC2・AC4）
 *   <li>荷主の画面（{@code /customer/**}）は荷主担当者、社内の画面（{@code /staff/**}）は営業担当者。ログインの時点の役割で判定する
 *       （request ごとに DB で確かめるのは W5。ADR-012）
 *   <li>session の固定化の防止（ログインで session ID を付け替える。既定）、CSRF の同期トークン（既定）
 *   <li>無操作 30 分（Spring Session の期限）と発行から 8 時間（{@link SessionLifetimeFilter}）。失効したら A-03
 *   <li>応答のヘッダー（SEC-17）
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /** 認証なしで開ける URL（ログイン、再認証の案内、エラー、静的な資源）。 */
    private static final String[] PUBLIC_PATHS = {
        "/login", SessionLifetime.EXPIRED_URL, "/error", "/css/**", "/js/**", "/webjars/**", "/favicon.ico"
    };

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
            Clock clock) {
        return new AuthenticationAuditListener(auditRecords, userDetails, companies, clock);
    }

    /**
     * password を先に照合し、利用停止・無効な企業はその後に拒否する。照合の前に拒否すると、誤った password でも
     * 利用停止であることが応答の時間から分かってしまう（AC2）。
     */
    @Bean
    AuthenticationManager authenticationManager(
            CargoUserDetailsService userDetails, PasswordEncoder passwordEncoder, ApplicationEventPublisher publisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetails);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        ProviderManager manager = new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
        return manager;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager, Clock clock)
            throws Exception {
        return http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .requestMatchers("/customer/**")
                        .hasRole("SHIPPER")
                        .requestMatchers("/staff/**")
                        .hasRole("SALES")
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage("/login")
                        .successHandler(new RoleHomeAuthenticationSuccessHandler(clock))
                        .failureUrl("/login?error"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .sessionManagement(session -> session.invalidSessionUrl(SessionLifetime.EXPIRED_URL))
                .addFilterBefore(new SessionLifetimeFilter(clock), UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'")))
                .build();
    }
}
