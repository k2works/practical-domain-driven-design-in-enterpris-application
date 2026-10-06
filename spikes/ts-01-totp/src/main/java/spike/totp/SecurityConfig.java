package spike.totp;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.DelegatingMissingAuthorityAccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.ApplicationEventPublisher;

/**
 * password（FACTOR_PASSWORD）と TOTP（FACTOR_TOTP）の両方の要素を、認証が要るすべての画面に求める（H1）。
 * 足りない要素に応じて、ログインの画面か認証コードの画面へ導く。
 */
@Configuration
@EnableMultiFactorAuthentication(authorities = {FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP})
public class SecurityConfig {

    @Bean
    AuthenticationManager authenticationManager(SpikeUsers users, ApplicationEventPublisher publisher) {
        DaoAuthenticationProvider password = new DaoAuthenticationProvider(users);
        ProviderManager manager = new ProviderManager(password, new TotpAuthenticationProvider(users));
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
        return manager;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager, Clock clock)
            throws Exception {
        return http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(requests -> requests.requestMatchers("/login", "/login/totp", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/login/totp", true))
                .addFilterAfter(new TotpAuthenticationFilter(authenticationManager, clock), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionLifetimeFilter(clock), AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(DelegatingMissingAuthorityAccessDeniedHandler.builder()
                        .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login/totp"), TotpAuthenticationToken.FACTOR_TOTP)
                        .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), FactorGrantedAuthority.PASSWORD_AUTHORITY)
                        .build()))
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
