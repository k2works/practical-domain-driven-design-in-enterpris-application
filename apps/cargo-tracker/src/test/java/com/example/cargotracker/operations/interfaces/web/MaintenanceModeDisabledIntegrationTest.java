package com.example.cargotracker.operations.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** メンテナンス表示は既定で無効で、フィルターを組み込まない（R-3。意図せず全画面が止まらない）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class MaintenanceModeDisabledIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void 既定ではフィルターがなくログインの画面を返す() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(context.getBeansOfType(MaintenanceFilter.class)).isEmpty();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("ただいまメンテナンス中です");
    }
}
