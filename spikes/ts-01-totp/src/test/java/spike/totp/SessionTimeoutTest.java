package spike.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** 無操作 30 分の session（US-18 AC1）は、サーブレットの session の期限の設定で守る。 */
@SpringBootTest
class SessionTimeoutTest {

    @Autowired
    Environment environment;

    @Test
    void sessionの無操作の期限は30分() {
        assertThat(environment.getProperty("server.servlet.session.timeout", Duration.class))
                .isEqualTo(Duration.ofMinutes(30));
    }
}
