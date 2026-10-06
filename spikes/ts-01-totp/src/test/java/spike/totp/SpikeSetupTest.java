package spike.totp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.SpringSecurityCoreVersion;

/** スパイクのプロジェクトが動き、Spring Security 7 系が入っていることを確かめる（ステップ 1）。 */
class SpikeSetupTest {

    @Test
    void Spring_Security_7系が入っている() {
        assertThat(SpringSecurityCoreVersion.getVersion()).startsWith("7.");
    }
}
