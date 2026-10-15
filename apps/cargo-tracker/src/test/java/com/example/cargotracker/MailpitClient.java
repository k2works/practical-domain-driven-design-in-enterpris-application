package com.example.cargotracker;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mailpit の HTTP の API で、届いたメールを宛先で読む（テストの部品。Bolt 33）。コンテナは同じ組み立てのテストで共有するので、宛先で絞る。
 */
public final class MailpitClient {

    /** 届いたメール 1 通。 */
    public record ReceivedMail(String from, List<String> to, String subject, String text) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;

    public MailpitClient(GenericContainer<?> mailpit) {
        this.baseUrl = "http://" + mailpit.getHost() + ":"
                + mailpit.getMappedPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT);
    }

    /** 宛先に届いたメールを、新しい順に返す。 */
    public List<ReceivedMail> messagesTo(String address) {
        JsonNode search =
                get("/api/v1/search?query=" + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
        List<ReceivedMail> mails = new ArrayList<>();
        for (JsonNode summary : search.path("messages")) {
            JsonNode message = get("/api/v1/message/" + summary.path("ID").asString());
            List<String> to = new ArrayList<>();
            message.path("To")
                    .forEach(recipient -> to.add(recipient.path("Address").asString()));
            mails.add(new ReceivedMail(
                    message.path("From").path("Address").asString(),
                    to,
                    message.path("Subject").asString(),
                    message.path("Text").asString()));
        }
        return mails;
    }

    /** 宛先にメールが届くまで待って返す（送信は別のスレッドで動くことがある）。 */
    public List<ReceivedMail> awaitMessagesTo(String address, int count, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        List<ReceivedMail> mails = messagesTo(address);
        while (mails.size() < count && Instant.now().isBefore(deadline)) {
            pause();
            mails = messagesTo(address);
        }
        return mails;
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Mailpit を読めない: " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mailpit を読む間に割り込まれた", e);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
