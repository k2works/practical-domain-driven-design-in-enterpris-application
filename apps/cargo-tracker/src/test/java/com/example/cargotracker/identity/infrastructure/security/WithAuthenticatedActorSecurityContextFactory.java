package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.Role;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
        CargoUserDetails details = new CargoUserDetails(
                (shipper ? TestActors.SHIPPER_USER : TestActors.STAFF_USER).value(),
                (shipper ? TestActors.SHIPPER_COMPANY : TestActors.STAFF_COMPANY).value(),
                shipper ? "shipper@example.com" : "sales@example.com",
                shipper ? "荷主 太郎" : "営業 一郎",
                shipper ? "荷主 A" : "A 社",
                Set.of(annotation.value()),
                true,
                null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        return context;
    }
}
