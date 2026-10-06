package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * ログインのメールアドレスから利用者を読み出す（Spring Security の利用者の読み出し）。
 * 利用停止・無効な企業は無効な主体にし、DaoAuthenticationProvider が password の照合の後に拒否する（IA-INV-09）。
 * 存在しない・形式の誤ったメールアドレスは見つからない扱いにし、画面には誤った password と同じ応答を返す（AC2）。
 */
public class CargoUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final CompanyRepository companies;

    public CargoUserDetailsService(UserRepository users, CompanyRepository companies) {
        this.users = users;
        this.companies = companies;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = findUser(username).orElseThrow(() -> new UsernameNotFoundException("利用者が見つからない"));
        Company company =
                companies.findById(user.companyId()).orElseThrow(() -> new IllegalStateException("利用者の所属企業がない"));
        return new CargoUserDetails(
                user.id().value(),
                company.id().value(),
                user.email().value(),
                user.displayName(),
                company.name(),
                user.roles(),
                user.authenticationRejection(company).isEmpty(),
                user.passwordHash());
    }

    /** メールアドレスの利用者を探す。形式の誤ったメールアドレスは見つからない扱い。 */
    Optional<User> findUser(String username) {
        try {
            return users.findByEmail(EmailAddress.of(username));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
