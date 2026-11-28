package com.example.cargotracker.quotation.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 1 回の送信の合計が multipart の上限（55 MB）を超えたときに、接続のリセットでなく 413 と案内の画面が返ることを、
 * 実際のポートで確かめる（Bolt 6〜8 レビュー R-08）。MockMvc は Tomcat を通らないため、ここだけ RANDOM_PORT にする。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UploadLimitIntegrationTest {

    private static final String BOUNDARY = "cargo-tracker-boundary";

    @LocalServerPort
    int port;

    @Test
    void 上限を超える送信には413と案内を返す() throws IOException, InterruptedException {
        HttpResponse<String> response = postTooLarge("/customer/transport-requests");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("送信したファイルが大きすぎます").contains("合計 55 MB");
    }

    @Test
    void 顧客Web以外への上限を超える送信には顧客の案内を返さない() throws IOException, InterruptedException {
        HttpResponse<String> response = postTooLarge("/staff/transport-requests/TR-2026-0001/reviews");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).doesNotContain("送信したファイルが大きすぎます");
    }

    /** ブラウザと同じく HTML を受け付ける要求で、上限（55 MB）を超える 56 MB のファイルを送る。 */
    private HttpResponse<String> postTooLarge(String path) throws IOException, InterruptedException {
        byte[] tooLarge = new byte[56 * 1024 * 1024];
        Arrays.fill(tooLarge, (byte) 'x');
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart("otherDocuments", "big.pdf", tooLarge)))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private static byte[] multipart(String name, String fileName, byte[] content) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
                        + "Content-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }
}
