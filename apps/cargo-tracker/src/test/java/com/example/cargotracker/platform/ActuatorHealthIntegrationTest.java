package com.example.cargotracker.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * ヘルスチェック（OBS-10。W10 の Bolt 53）。ALB のヘルスチェックは認証なしで読める管理用のポートで受け、
 * 業務のポートには出さない。管理用のポートに出すのはヘルスだけにする（`/actuator/health` だけを通す専用の連鎖
 * `SecurityConfiguration.actuatorFilterChain`。業務の連鎖は変えない）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class ActuatorHealthIntegrationTest {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private HttpResponse<String> get(int onPort, String path) throws Exception {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 管理用のポートのヘルスは認証なしでUPを返す() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void 管理用のポートにはヘルス以外を出さない() throws Exception {
        // 出していないエンドポイントは拒否（403）か見つからない（404）。200 にならなければよい
        assertThat(get(managementPort, "/actuator/env").statusCode()).isIn(403, 404);
        assertThat(get(managementPort, "/actuator/beans").statusCode()).isIn(403, 404);
    }

    @Test
    void ヘルス以外は連鎖が拒否する_出していないので404ではなく403で固定する() throws Exception {
        // denyAll を permitAll に変えると 404（エンドポイントがない）に変わるので、連鎖の拒否が効いていることを 403 で押さえる
        assertThat(get(managementPort, "/actuator/env").statusCode()).isEqualTo(403);
        assertThat(get(managementPort, "/actuator/beans").statusCode()).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0} はヘルス以外の中身を返さない")
    @ValueSource(
            strings = {
                "/actuator/health/../env",
                "/actuator/health/%2e%2e/env",
                "/actuator/env;x=1",
                "/actuator/%65nv",
                "/actuator//env",
            })
    void パスの変形でヘルス以外を読ませない(String path) throws Exception {
        HttpResponse<String> response = get(managementPort, path);

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("activeProfiles").doesNotContain("propertySources");
    }

    @Test
    void GET以外ではヘルスも読めない_シャットダウンも出さない() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> post = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/shutdown"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(post.statusCode()).isNotEqualTo(200);
    }

    @Test
    void 業務のポートにはActuatorを出さない() throws Exception {
        HttpResponse<String> response = get(port, "/actuator/health");

        assertThat(response.statusCode()).isNotEqualTo(200);
    }
}
