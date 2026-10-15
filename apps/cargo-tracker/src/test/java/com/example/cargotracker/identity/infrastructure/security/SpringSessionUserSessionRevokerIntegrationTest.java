package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** 利用者の session の失効（Spring Session JDBC の主体の名前の索引。SEC-08、Bolt 33a）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpringSessionUserSessionRevokerIntegrationTest {

    @Autowired
    SpringSessionUserSessionRevoker revoker;

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    private String sessionOf(String principalName) {
        return save(sessions, principalName);
    }

    private static <S extends Session> String save(FindByIndexNameSessionRepository<S> repository, String principal) {
        S session = repository.createSession();
        if (principal != null) {
            session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
        }
        repository.save(session);
        return session.getId();
    }

    @Test
    void その利用者のsessionだけをすべて消す() {
        String email = "revoke-" + UUID.randomUUID() + "@example.com";
        String other = "other-" + UUID.randomUUID() + "@example.com";
        String first = sessionOf(email);
        String second = sessionOf(email);
        String othersSession = sessionOf(other);
        String anonymous = sessionOf(null);
        assertThat(sessions.findById(first)).as("消す前は session がある（空振りしない）").isNotNull();

        int revoked = revoker.revokeAll(EmailAddress.of(email));

        assertThat(revoked).isEqualTo(2);
        assertThat(sessions.findById(first)).isNull();
        assertThat(sessions.findById(second)).isNull();
        assertThat(sessions.findById(othersSession)).as("ほかの利用者の session は残す").isNotNull();
        assertThat(sessions.findById(anonymous)).as("主体のない session は残す").isNotNull();
    }
}
