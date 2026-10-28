package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * {@link WithAuthenticatedActor} の主体を、本物と同じ {@link CargoUserDetails} で作る。
 */
public class WithAuthenticatedActorSecurityContextFactory
        implements WithSecurityContextFactory<WithAuthenticatedActor> {

    @Override
    public SecurityContext createSecurityContext(WithAuthenticatedActor annotation) {
        boolean shipper = annotation.value() == Role.SHIPPER;
        AuthenticatedActor actor = new AuthenticatedActor(
                shipper ? TestActors.SHIPPER_USER : TestActors.STAFF_USER,
                shipper ? TestActors.SHIPPER_COMPANY : TestActors.STAFF_COMPANY,
                Set.of(annotation.value()),
                shipper ? "荷主 太郎" : "営業 一郎",
                shipper ? "荷主 A" : "A 社",
                RoleAssignmentPolicy.holdsAssignmentScopedRole(Set.of(annotation.value()))
                        ? Set.of(TestActors.STAFF_ASSIGNED_COMPANY)
                        : Set.of());
        CargoUserDetails details =
                new CargoUserDetails(actor, shipper ? "shipper@example.com" : "sales@example.com", true, null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        // 本番と同じく 2 つの要素（password と TOTP）がそろった主体にする（ADR-012 の見直しの 4、Bolt 31c）
        List<GrantedAuthority> authorities = new ArrayList<>(details.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        authorities.add(FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP));
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, authorities));
        return context;
    }
}
