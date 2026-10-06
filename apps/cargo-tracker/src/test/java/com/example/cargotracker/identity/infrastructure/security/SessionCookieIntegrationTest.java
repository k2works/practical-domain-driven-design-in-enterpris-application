package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * session の Cookie の属性（SEC-17）。Spring Boot は組み込みのサーバーで動くときだけ server.servlet.session.cookie の設定を
 * Spring Session の Cookie に当てるため、MockMvc ではなく実際の HTTP で確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void sessionのCookieはスクリプトから読めずHTTPSでだけ送られ別のサイトのPOSTで送られない() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());

        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("SESSION=")
                        .contains("HttpOnly")
                        .contains("Secure")
                        .contains("SameSite=Lax"));
    }
}
