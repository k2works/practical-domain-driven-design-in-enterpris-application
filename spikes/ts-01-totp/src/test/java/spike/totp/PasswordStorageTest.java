package spike.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** password の保存（SEC-07: Argon2id または bcrypt）。Spring Security 7 の既定と、Argon2id に要るものを確かめる。 */
class PasswordStorageTest {

    @Test
    void 既定の委譲する符号化はbcryptで保存し照合できる() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        String encoded = encoder.encode(SpikeUsers.PASSWORD);

        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoder.matches(SpikeUsers.PASSWORD, encoded)).isTrue();
    }

    @Test
    void Argon2idはBouncy_Castleがないと使えない() {
        assertThatThrownBy(() -> Class.forName("org.bouncycastle.crypto.params.Argon2Parameters"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
