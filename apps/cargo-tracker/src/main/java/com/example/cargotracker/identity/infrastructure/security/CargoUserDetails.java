package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.io.Serial;
import java.util.Collection;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security の認証の主体。session（Spring Session JDBC）に直列化して置くため、値は直列化できる型だけで持ち、
 * ほかのコンテキストには {@link #actor()} で共有カーネルの型を渡す（ADR-012）。
 * 利用停止・無効な企業は {@link #isEnabled()} が false になり、password の照合の後に拒否する（IA-INV-09）。
 */
public final class CargoUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID companyId;
    private final String email;
    private final String displayName;
    private final String companyName;
    private final EnumSet<Role> roles;
    private final boolean enabled;
    private String passwordHash;

    /**
     * @param actor 認証された利用者（値を直列化できる型に写して持つ）
     * @param email ログインのメールアドレス
     * @param enabled 認証できるか（利用停止・無効な企業なら false。IA-INV-09）
     * @param passwordHash password のハッシュ（認証の後に消す）
     */
    CargoUserDetails(AuthenticatedActor actor, String email, boolean enabled, String passwordHash) {
        this.userId = actor.userId().value();
        this.companyId = actor.companyId().value();
        this.email = email;
        this.displayName = actor.displayName();
        this.companyName = actor.companyName();
        this.roles = EnumSet.copyOf(actor.roles());
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    /** ほかのコンテキストに渡す、認証された利用者。 */
    public AuthenticatedActor actor() {
        return new AuthenticatedActor(new UserId(userId), new CompanyId(companyId), roles, displayName, companyName);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
