package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.Role;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 画面の単体テストを、認証された利用者（{@link TestActors}）として動かす（Bolt 14）。荷主担当者は荷主の企業、
 * それ以外の役割は A 社の利用者にする。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithAuthenticatedActorSecurityContextFactory.class)
public @interface WithAuthenticatedActor {

    Role value() default Role.SHIPPER;
}
