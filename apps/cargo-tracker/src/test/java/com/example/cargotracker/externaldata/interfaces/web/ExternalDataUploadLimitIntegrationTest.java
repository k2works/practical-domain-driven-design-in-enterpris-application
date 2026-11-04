package com.example.cargotracker.externaldata.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.rules.IngestionFileLimit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 外部データの取込への 1 回の送信が multipart の上限（55 MB）を超えたときに、413 と取込の案内が返ることを実際のポートで確かめる
 * （Bolt 39c2 の行 4。前例 {@code UploadLimitIntegrationTest}）。超過は CSRF の確認が送信の本体を読むのでコントローラーより前で起き、
 * 認証の前に 413 の案内になる（error/413.html）。前例は quotation のパッケージにあり、外部データのパスを足すとコンテキストの独立が崩れるので、
 * 外部データの側に置く。MockMvc は Tomcat を通らないため RANDOM_PORT にする。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExternalDataUploadLimitIntegrationTest {

    private static final String BOUNDARY = "cargo-tracker-boundary";
    private static final String HEADING = "<h1>取込ファイルが大きすぎます</h1>";

    @LocalServerPort
    int port;

    @Test
    void 行4_取込への上限を超える送信には413と取込の案内を送信が拒まれた印の中に返し見積依頼の案内を出さない() throws IOException, InterruptedException {
        HttpResponse<String> response = postTooLarge("/staff/external-data/ingestions");

        assertThat(response.statusCode()).isEqualTo(413);
        String limit = "1 ファイルにつきデータ行 %s 行・%d MB までです。"
                .formatted(
                        String.format(Locale.ROOT, "%,d", IngestionFileLimit.MAX_DATA_LINES),
                        IngestionFileLimit.MAX_BYTES / (1024 * 1024));
        String main = mainOf(response.body());
        assertThat(main)
                .startsWith("<main data-session-input=\"rejected\"")
                .contains(HEADING)
                .contains(limit)
                .contains("ブラウザの「戻る」で外部データの取込の画面に戻り、ファイルを分けて取り込んでください。")
                .contains("<a href=\"/staff/external-data/ingestions/new\">外部データの取込へ</a>");
        assertThat(response.body()).doesNotContain("送信したファイルが大きすぎます").doesNotContain("<h1>送信が大きすぎます</h1>");
    }

    @Test
    void 行4_取込でない社内の送信の413には取込の案内を出さず一般の案内のまま() throws IOException, InterruptedException {
        HttpResponse<String> response = postTooLarge("/staff/transport-requests/TR-2026-0001/reviews");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("<h1>送信が大きすぎます</h1>").doesNotContain(HEADING);
    }

    /** 413 の案内の main 要素（始まりの印から終わりまで）。 */
    private static String mainOf(String body) {
        int start = body.indexOf("<main");
        int end = body.indexOf("</main>");
        assertThat(start).as("main の始まり").isNotNegative();
        assertThat(end).as("main の終わり").isGreaterThan(start);
        return body.substring(start, end);
    }

    /** ブラウザと同じく HTML を受け付ける要求で、上限（55 MB）を超える 56 MB のファイルを送る。 */
    private HttpResponse<String> postTooLarge(String path) throws IOException, InterruptedException {
        byte[] tooLarge = new byte[56 * 1024 * 1024];
        Arrays.fill(tooLarge, (byte) 'x');
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(tooLarge)))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private static byte[] multipart(byte[] content) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"voyage.csv\"\r\n"
                        + "Content-Type: text/csv\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }
}
