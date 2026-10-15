package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 利用者の session の失効の実装（Bolt 33a、SEC-08、ADR-012）。Spring Session の主体の名前（ログインのメールアドレス。
 * {@code CargoUserDetails.getUsername()}）の索引で利用者の session を探して消す。Spring Session JDBC は自分のトランザクション
 * （REQUIRES_NEW）で動くので、呼ぶ側の確定とは別に確定する（呼ぶ側は確定の後に呼ぶ）。主体のない session（未認証）は対象にならない。
 */
@Component
public class SpringSessionUserSessionRevoker implements UserSessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SpringSessionUserSessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public int revokeAll(EmailAddress email) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(email.value());
        found.keySet().forEach(sessions::deleteById);
        return found.size();
    }
}
