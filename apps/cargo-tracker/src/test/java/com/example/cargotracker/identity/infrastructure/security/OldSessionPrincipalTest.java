package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * 古い session の主体（Bolt 38f の行 7、決定 (2)）。担当企業は直列化しない（{@code transient}）ので、Bolt 38f より前に保存した
 * session のバイト列もそのまま戻り、担当企業は空になる（失敗に倒す）。次の request の確かめで今の担当企業が載る。資源は、Bolt 38f で
 * {@link CargoUserDetails} を変える前に、session に置く形（{@link SecurityContextImpl}）を JDK の直列化で書いたもの。
 */
class OldSessionPrincipalTest {

    private static final String OLD_SESSION = "old-session-principal.b64";

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static CargoUserDetails principalOf(Object context) {
        return (CargoUserDetails)
                ((SecurityContext) context).getAuthentication().getPrincipal();
    }

    @Test
    void 変える前に保存したsessionの主体はそのまま戻り担当企業は空() throws Exception {
        byte[] oldBytes;
        try (InputStream in = Objects.requireNonNull(OldSessionPrincipalTest.class.getResourceAsStream(OLD_SESSION))) {
            oldBytes = Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim());
        }

        AuthenticatedActor actor = principalOf(deserialize(oldBytes)).actor();

        assertThat(actor.roles()).containsExactly(Role.SALES);
        assertThat(actor.displayName()).isEqualTo("営業 一郎");
        assertThat(actor.assignedCompanies()).isEmpty();
        assertThat(actor.hasNoAssignedCompanies()).isTrue();
    }

    @Test
    void 担当企業は直列化せず戻した主体では空になる() throws Exception {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        AuthenticatedActor withAssigned = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SALES),
                "営業 一郎",
                "A 社",
                Set.of(shipper));
        CargoUserDetails details = new CargoUserDetails(withAssigned, "sales@example.com", true, null);
        SecurityContextImpl context = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));

        assertThat(details.actor().assignedCompanies()).containsExactly(shipper);
        assertThat(principalOf(deserialize(serialize(context))).actor().assignedCompanies())
                .isEmpty();
    }
}
