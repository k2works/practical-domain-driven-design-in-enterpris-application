package com.example.cargotracker.operations.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * メンテナンス表示（ADR-023。W10-1 の Bolt 59。CP-4）。有効なとき、業務のポートはすべての要求に認証の前で 503 を返し、
 * ヘルスチェックの管理用のポートは通す（ALB がタスクを外さない）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "cargotracker.maintenance.enabled=true"})
@Import(TestcontainersConfiguration.class)
class MaintenanceModeIntegrationTest {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private HttpResponse<String> send(int onPort, String method, String path) throws Exception {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path))
                                .method(method, HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /",
        "GET, /login",
        "POST, /login",
        "GET, /customer/transport-requests",
        "GET, /staff/transport-requests",
        "GET, /css/app.css",
        "GET, /actuator/health",
        "DELETE, /no-such-path",
    })
    void 業務のポートはすべての要求に503と案内とRetryAfterを返しsessionを作らない(String method, String path) throws Exception {
        HttpResponse<String> response = send(port, method, path);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).hasValue("1800");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/html;charset=UTF-8");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(response.body()).contains("<html lang=\"ja\">", "ただいまメンテナンス中です");
    }

    @Test
    void 管理用のポートのヘルスは通す() throws Exception {
        HttpResponse<String> response = send(managementPort, "GET", "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}
