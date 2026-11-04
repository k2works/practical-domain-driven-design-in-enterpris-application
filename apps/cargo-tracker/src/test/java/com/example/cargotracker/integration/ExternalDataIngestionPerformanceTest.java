package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * PERF-06（1 万件のファイルを 5 分以内に分類・採用）を PostgreSQL 18 で測る（Bolt 39b4 の行 1・2・10・11・12、Bolt 39c2 の行 21 の画面の経路。non_functional の PERF-06）。
 * 時間は CSV の組み立て（{@link IngestFileCommandAssembler#assemble}）の前から取込のサービスが戻るまで（取込のコミットを含む）で、
 * 監査の listener の配信はコミットの後の非同期で PERF-06 の外なので、完了までを待って参考の値としてログに出す。測った値（全体・組み立て・
 * 取込、接続の最大とコミットの数、分類ごとの件数の照会の合計、ファイルの大きさ）はテストのログに出す。
 *
 * <p>時間を測るので {@code test} から外し、{@code performanceTest} で動かす（{@code check} と push の CI に入れない。行 8 の規則）。
 * PERF-06 の判定は Linux の CI の手動のジョブ {@code performance} で行う（Windows の Docker Desktop のポート転送は 8 KB を超える文ごとに
 * 約 41 ms の人工の遅れを足し、寄港 60 の航海が 5 分を超える）。CI のログでは「PERF-06」で始まる行が測った値になる。すべての行が
 * 採用になる最も重い経路（外部原本・採用値・イベントを書く）で、外部 ID は行ごとに違える。確かめる行は自分の取込 ID と UUID を含む提供元の
 * 名前で絞り、全体で数えない（test_strategy の数えるテストの規約）。テストの終わりに自分の取込の監査の発行記録がすべて完了になるまで待つ
 * （T-184）。情報源は最後に停止中にする。
 */
@Tag("performance")
@Tag("NFR-PERF-06")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExternalDataIngestionPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalDataIngestionPerformanceTest.class);

    private static final int ROWS = 10_000;

    /** PERF-06 の判定（組み立てから取込のコミットまで）。 */
    private static final Duration LIMIT = Duration.ofMinutes(5);

    /** 分類ごとの件数の照会の合計の上限（超えたら 1 本の照会にする。開始準備の決定 (6)）。 */
    private static final Duration COUNT_QUERIES_LIMIT = Duration.ofSeconds(1);

    /** 1 万件の監査の配信を待つ上限（設計の決定「共有の DB の他の行」。超えたら落とす）。 */
    private static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(10);

    /** 原本の大きさの上限（ファイル全体。「人に諮ること」1 の決定 (a)。40 MiB）。 */
    private static final long MAX_FILE_BYTES = 41_943_040L;

    private static final String VOYAGE_HEADER = "external_id,external_version,acquired_at,port_calls";

    /** 寄港の時刻の書式（マイクロ秒と UTC。採用値の要約と同じ形で、秒までの形より長い）。 */
    private static final DateTimeFormatter PORT_CALL_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    /** 寄港の時刻の始まり（行と寄港ごとにずらす）。 */
    private static final Instant FIRST_CALL = Instant.parse("2026-12-01T00:00:00Z");

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String AUDIT_LISTENER = ExternalRecordAuditEventHandler.class.getSimpleName();

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    /** 行 21 で {@code ingest} の前後の時刻を取るため spy にする（ほかの行は本物をそのまま呼ぶ）。 */
    @MockitoSpyBean
    IngestionService ingestionService;

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ConnectionUsageRecorder connectionUsage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void 行1と行10と行11_寄港5の航海1万件は組み立てから取込のコミットまで5分以内で採用1万件になり監査の配信が完了する() {
        byte[] csv = voyageCsv(5, acquiredAt());

        measure("voyage(port-calls=5)", "VOYAGE", CsvFileKind.VOYAGE, csv);
    }

    @Test
    void 行2と行10と行11_主要実績1万件は6種類を順に回しても5分以内で採用1万件になり監査の配信が完了する() {
        Instant acquiredAt = acquiredAt();
        MilestoneKindValue[] kinds = MilestoneKindValue.values();
        byte[] csv = ExternalDataIngestionConnectionTest.csv(
                ExternalDataIngestionConnectionTest.MILESTONE_HEADER,
                ROWS,
                row -> "MS-%05d,1,%s,%s,%s,JPTYO,%s"
                        .formatted(
                                row,
                                acquiredAt,
                                ExternalDataIngestionConnectionTest.trackingNumber(row),
                                kinds[row % kinds.length],
                                acquiredAt));

        measure("milestone", "MILESTONE", CsvFileKind.MILESTONE, csv);
    }

    @Test
    void 行12_寄港60の航海1万件は5分以内でファイルは原本の大きさの上限の40MiB以下になる() {
        Instant acquiredAt = acquiredAt();
        IntFunction<String> line = voyageLine(VoyageValues.MAX_PORT_CALLS, acquiredAt);
        byte[] csv = ExternalDataIngestionConnectionTest.csv(VOYAGE_HEADER, ROWS, line);
        int maxLineBytes = 0;
        for (int row = 0; row < ROWS; row++) {
            maxLineBytes = Math.max(maxLineBytes, line.apply(row).getBytes(StandardCharsets.UTF_8).length);
        }
        LOG.info(
                "PERF-06 voyage(port-calls=60) file-bytes={} limit-bytes={} max-row-bytes={}",
                csv.length,
                MAX_FILE_BYTES,
                maxLineBytes);

        assertThat((long) csv.length).isLessThanOrEqualTo(MAX_FILE_BYTES);
        measure("voyage(port-calls=60)", "VOYAGE", CsvFileKind.VOYAGE, csv);
    }

    /**
     * 行 21（Bolt 39c2。T-242）: 画面の経路で上限いっぱい（データ行 10,000 行）のファイルを取り込む。MockMvc の multipart で画面の POST
     * （本物のフィルターの連なりと、password と TOTP でログインしたデータ責任者の session と CSRF のトークン）から結果の画面への移動（302）
     * までを測る。Tomcat の multipart の受け取りは MockMvc の外なので範囲の外。取込のサービスの spy で {@code ingest} の呼び出しの前後の
     * 時刻を取り、POST 全体から「取込の前（フィルター・束縛・ファイルの読み出し・組み立て）」「取込（コミットまで）」「取込の後（PRG の応答）」
     * に分ける（T-241）。
     */
    @Test
    void 行21_画面のPOSTで寄港5の航海1万件のファイルを送ると結果の画面への移動まで5分以内で採用1万件になる() throws Exception {
        byte[] csv = voyageCsv(5, acquiredAt());
        UUID sourceId = insertApprovedSource(ExternalDataIngestionConnectionTest.newProvider(), "VOYAGE");
        Cookie session = login(dataSteward());
        AtomicLong ingestStarted = new AtomicLong();
        AtomicLong ingestFinished = new AtomicLong();
        AtomicReference<IngestionOutcome> outcome = new AtomicReference<>();
        doAnswer(invocation -> {
                    ingestStarted.set(System.nanoTime());
                    try {
                        IngestionOutcome value = (IngestionOutcome) invocation.callRealMethod();
                        outcome.set(value);
                        return value;
                    } finally {
                        ingestFinished.set(System.nanoTime());
                    }
                })
                .when(ingestionService)
                .ingest(any(), any());
        Ingested result = null;
        try {
            MockMultipartHttpServletRequestBuilder request =
                    (MockMultipartHttpServletRequestBuilder) multipart("/staff/external-data/ingestions")
                            .file(new MockMultipartFile("file", "voyage.csv", "text/csv", csv))
                            .param("dataSourceId", sourceId.toString())
                            .cookie(session)
                            .with(csrf());
            long started = System.nanoTime();
            MvcResult response = mvc.perform(request).andReturn();
            long finished = System.nanoTime();
            result = ExternalDataIngestionConnectionTest.ingested(outcome.get());
            LOG.info(
                    "PERF-06 web voyage(port-calls=5) rows={} file-bytes={} before-ingest={} ingest={} after-ingest={}"
                            + " total={} limit={}",
                    result.receivedCount(),
                    csv.length,
                    seconds(ingestStarted.get() - started),
                    seconds(ingestFinished.get() - ingestStarted.get()),
                    seconds(finished - ingestFinished.get()),
                    seconds(finished - started),
                    seconds(LIMIT.toNanos()));

            assertThat(response.getResponse().getStatus()).isEqualTo(302);
            assertThat(response.getResponse().getRedirectedUrl())
                    .isEqualTo("/staff/external-data/ingestions/" + result.ingestionId());
            assertThat(Duration.ofNanos(finished - started)).isLessThanOrEqualTo(LIMIT);
            assertThat(result.receivedCount()).isEqualTo(ROWS);
            assertThat(result.countOf(Classification.ADOPTED)).isEqualTo(ROWS);
            assertThat(adoptedValueCount(result.ingestionId())).isEqualTo(ROWS);
        } finally {
            finish("web voyage(port-calls=5)", result, sourceId);
        }
    }

    /** 画面の経路のデータ責任者（運営会社の利用者。TOTP を登録済み）。メールアドレスを返す。 */
    private String dataSteward() {
        Company operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        String email = "external-data-performance-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                operator.id(),
                EmailAddress.of(email),
                "データ責任者",
                passwordEncoder.encode(IdentityLogins.PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.DATA_STEWARD)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private Cookie login(String email) throws Exception {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock).login(email);
    }

    /**
     * 1 つのファイルを組み立てから取り込み、測った値をログに出して判定し、監査の配信の完了を待つ。配信の完了待ちは判定が落ちても
     * {@code finally} で（情報源を停止にする前に）行い、1 万件の非同期の配信を裏で走らせたまま次の計測に進まない。
     */
    private void measure(String label, String recordType, CsvFileKind kind, byte[] csv) {
        UUID sourceId = insertApprovedSource(ExternalDataIngestionConnectionTest.newProvider(), recordType);
        Ingested result = null;
        try {
            connectionUsage.start();
            long started = System.nanoTime();
            IngestFileCommand command;
            long assembled;
            try {
                command = IngestFileCommandAssembler.assemble(sourceId, kind, csv);
                assembled = System.nanoTime();
                result = ExternalDataIngestionConnectionTest.ingested(ingestionService.ingest(steward, command));
            } finally {
                connectionUsage.stop();
            }
            long finished = System.nanoTime();
            Duration total = Duration.ofNanos(finished - started);
            LOG.info(
                    "PERF-06 {} rows={} file-bytes={} assemble={} ingest={} total={} limit={}",
                    label,
                    command.receivedCount(),
                    csv.length,
                    seconds(assembled - started),
                    seconds(finished - assembled),
                    seconds(finished - started),
                    seconds(LIMIT.toNanos()));
            Duration countQueries = assertClassificationCounts(result);
            LOG.info(
                    "PERF-06 {} connections peak={} commits={} classification-count-queries={} ({})",
                    label,
                    connectionUsage.peakConnections(),
                    connectionUsage.commits(),
                    Classification.values().length,
                    seconds(countQueries.toNanos()));

            assertThat(total).isLessThanOrEqualTo(LIMIT);
            assertThat(result.receivedCount()).isEqualTo(ROWS);
            assertThat(result.countOf(Classification.ADOPTED)).isEqualTo(ROWS);
            assertThat(adoptedValueCount(result.ingestionId())).isEqualTo(ROWS);
            assertThat(connectionUsage.peakConnections()).isEqualTo(2);
            assertThat(connectionUsage.commits()).isEqualTo(ROWS + 2);
            assertThat(countQueries).isLessThanOrEqualTo(COUNT_QUERIES_LIMIT);
        } finally {
            finish(label, result, sourceId);
        }
    }

    /** 取り込めていれば監査の配信の完了を待ち（参考の値としてログに出す）、待てても待てなくても情報源を停止にする。 */
    private void finish(String label, Ingested result, UUID sourceId) {
        try {
            if (result != null) {
                long deliveryStarted = System.nanoTime();
                awaitCompletedAuditPublications(result);
                LOG.info(
                        "PERF-06 {} audit delivery completed in {} (reference, outside PERF-06)",
                        label,
                        seconds(System.nanoTime() - deliveryStarted));
            }
        } finally {
            removeSource(sourceId);
        }
    }

    /**
     * 行 11: 分類ごとの件数を取込のサービスと同じ照会（取込 ID と分類で絞る。分類の数だけ）で数え、行の結果の分類と一致することを
     * 確かめ、照会の合計の時間を返す。
     */
    private Duration assertClassificationCounts(Ingested result) {
        long started = System.nanoTime();
        int[] counts = new int[Classification.values().length];
        for (Classification classification : Classification.values()) {
            counts[classification.ordinal()] = count(
                    "SELECT COUNT(*) FROM external_data.received_record WHERE ingestion_id = ? AND classification = ?",
                    result.ingestionId(),
                    classification.name());
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        for (Classification classification : Classification.values()) {
            long fromRows = result.rows().stream()
                    .filter(row -> row.classification() == classification)
                    .count();
            assertThat(counts[classification.ordinal()])
                    .as("分類 %s", classification)
                    .isEqualTo((int) fromRows);
            assertThat(result.countOf(classification))
                    .as("分類 %s", classification)
                    .isEqualTo((int) fromRows);
        }
        return elapsed;
    }

    /** 自分の取込の監査の発行記録がすべて完了になるまで待つ（待つ条件は自分の取込 ID と監査の受け手。T-184）。 */
    private void awaitCompletedAuditPublications(Ingested result) {
        int expected = result.countOf(Classification.ADOPTED) + result.countOf(Classification.QUARANTINED);
        await().atMost(DELIVERY_TIMEOUT)
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> count(
                                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ?"
                                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                                "%" + result.ingestionId() + "%",
                                "%" + AUDIT_LISTENER + "%")
                        == expected);
    }

    /** 寄港の数の決まった航海のファイル（航海番号は行ごとに違え、すべて採用になる。T-230）。 */
    private static byte[] voyageCsv(int portCalls, Instant acquiredAt) {
        return ExternalDataIngestionConnectionTest.csv(VOYAGE_HEADER, ROWS, voyageLine(portCalls, acquiredAt));
    }

    /**
     * 航海の行。寄港は港を違え、時刻を順に並べ（寄港ごとに 1 日、到着から出発まで 12 時間）、始港の到着と終港の出発を空にする（T-230）。
     */
    private static IntFunction<String> voyageLine(int portCalls, Instant acquiredAt) {
        return row -> {
            StringJoiner calls = new StringJoiner("|");
            Instant base = FIRST_CALL.plus(row, ChronoUnit.MINUTES);
            for (int call = 0; call < portCalls; call++) {
                Instant arrival = base.plus(call, ChronoUnit.DAYS);
                Instant departure = arrival.plus(12, ChronoUnit.HOURS);
                calls.add(port(call) + ";" + (call == 0 ? "" : PORT_CALL_TIME.format(arrival)) + ";"
                        + (call == portCalls - 1 ? "" : PORT_CALL_TIME.format(departure)));
            }
            return "V%05d,1,%s,%s".formatted(row, acquiredAt, calls);
        };
    }

    /** 寄港ごとに違う UN/LOCODE（{@code JP} と英大文字 3 字）。 */
    private static String port(int call) {
        return "JP" + LETTERS.charAt(call / (26 * 26) % 26) + LETTERS.charAt(call / 26 % 26)
                + LETTERS.charAt(call % 26);
    }

    private static String seconds(long nanos) {
        return "%.1fs".formatted(nanos / 1_000_000_000.0);
    }

    /** 取得時刻（取込時刻より前。秒まで。T-230）。 */
    private Instant acquiredAt() {
        return clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    private int adoptedValueCount(UUID ingestionId) {
        return count(
                "SELECT COUNT(*) FROM external_data.adopted_value a JOIN external_data.received_record r"
                        + " ON r.id = a.received_record_id WHERE r.ingestion_id = ?",
                ingestionId);
    }

    private UUID insertApprovedSource(String provider, String recordType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, 'APPROVED', 0)",
                id,
                provider,
                recordType);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない（取込が残るので消せない。停止中にする）。 */
    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
