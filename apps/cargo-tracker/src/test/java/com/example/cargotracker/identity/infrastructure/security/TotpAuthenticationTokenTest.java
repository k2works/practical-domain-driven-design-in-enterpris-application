package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * 認証の結果のトークンの経路（Bolt 33e）。経路は session に保存しない（{@code transient}。ローリング配備の間に古い版が新しい版の session を
 * 読めるよう、直列化の形を変えない）。同じ request の中の権限の合わせ（{@code toBuilder()}）では経路を写す。
 */
class TotpAuthenticationTokenTest {

    private static final FactorGrantedAuthority FACTOR =
            FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP);

    @Test
    void 経路はsessionの直列化に入らず読み直すとnullになる() throws Exception {
        TotpAuthenticationToken token =
                TotpAuthenticationToken.authenticated("shipper", FACTOR, SecondFactorPath.RECOVERY_CODE);

        byte[] bytes = serialize(token);
        TotpAuthenticationToken restored = (TotpAuthenticationToken) deserialize(bytes);

        assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                .as("古い版が知らない型を直列化に入れない")
                .doesNotContain(SecondFactorPath.class.getName());
        assertThat(restored.path()).isNull();
        assertThat(restored.getPrincipal()).isEqualTo("shipper");
        assertThat(restored).as("経路は等しさに入れない").isEqualTo(token);
    }

    @Test
    void 権限の合わせで作り直しても経路を写す() {
        Authentication rebuilt =
                TotpAuthenticationToken.authenticated("shipper", FACTOR, SecondFactorPath.RECOVERY_CODE).toBuilder()
                        .build();

        assertThat(((TotpAuthenticationToken) rebuilt).path()).isEqualTo(SecondFactorPath.RECOVERY_CODE);
    }

    @Test
    void 経路のない確かめた後のトークンは作れない() {
        assertThatThrownBy(() -> TotpAuthenticationToken.authenticated("shipper", FACTOR, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(out)) {
            stream.writeObject(value);
        }
        return out.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return stream.readObject();
        }
    }
}
