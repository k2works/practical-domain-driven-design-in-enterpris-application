package com.example.cargotracker.externaldata.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Unreadable;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 形式の誤りの隔離の受入シナリオのステップ（UC-15 2a、US-14、BR-12、ED-INV-02。Bolt 39b2）。CSV のバイト列から組み立て
 * （{@code interfaces.file} の {@link IngestFileCommandAssembler}）を通して取込のサービスを呼ぶ。情報源は 39b の
 * {@code ExternalRecordIngestionSteps} の前提で入れ、提供元の名前で引く。
 */
public class ExternalRecordFormatErrorSteps {

    /** 受入シナリオのデータ責任者。 */
    private static final AuthenticatedActor DATA_STEWARD = new AuthenticatedActor(
            new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b2001")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000039b2002")),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    private final InMemoryDataSourceRepository dataSources;
    private final InMemoryIngestionRepository ingestions;
    private final InMemoryExternalRecordRepository externalRecords;
    private final InMemoryReceivedRecordRepository receivedRecords;
    private final InMemoryExternalPayloadStorage payloadStorage;
    private final IngestionService ingestionService;

    private String csv;
    private Ingested result;

    public ExternalRecordFormatErrorSteps(
            InMemoryDataSourceRepository dataSources,
            InMemoryIngestionRepository ingestions,
            InMemoryExternalRecordRepository externalRecords,
            InMemoryReceivedRecordRepository receivedRecords,
            InMemoryExternalPayloadStorage payloadStorage,
            IngestionService ingestionService) {
        this.dataSources = dataSources;
        this.ingestions = ingestions;
        this.externalRecords = externalRecords;
        this.receivedRecords = receivedRecords;
        this.payloadStorage = payloadStorage;
        this.ingestionService = ingestionService;
    }

    @もし("データ責任者が提供元 {string} の情報源から次の CSV を取り込む")
    public void CSVを取り込む(String provider, String content) {
        ingest(provider, content, StandardCharsets.UTF_8);
    }

    @もし("データ責任者が提供元 {string} の情報源から次の CSV を Shift_JIS で取り込む")
    public void ShiftJISのCSVを取り込む(String provider, String content) {
        ingest(provider, content, Charset.forName("Shift_JIS"));
    }

    @ならば("受け取った件数は {int} 件で、件数は突き合う")
    public void 件数は突き合う(int receivedCount) {
        assertThat(result.receivedCount()).isEqualTo(receivedCount);
        assertThat(ingestion().receivedCount()).isEqualTo(receivedCount);
        assertThat(result.reconciles()).isTrue();
    }

    @ならば("取込は要確認で、{int} 行目から {int} 行を取り込めなかった")
    public void 取込は要確認(int fromLine, int lineCount) {
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.unreadable().fromLine()).isEqualTo(fromLine);
        assertThat(result.unreadable().lineCount()).isEqualTo(lineCount);
        assertThat(ingestion().unparsedFromLine()).isEqualTo(fromLine);
        assertThat(ingestion().unparsedLineCount()).isEqualTo(lineCount);
    }

    @ならば("取込は要確認でない")
    public void 取込は要確認でない() {
        assertThat(result.requiresReview()).isFalse();
        assertThat(ingestion().unparsedFromLine()).isNull();
        assertThat(ingestion().unparsedLineCount()).isZero();
    }

    @ならば("取り込めなかった部分は外部原本を持たない隔離で、注記は {string} である")
    public void 取り込めなかった部分の隔離(String note) {
        assertKeylessQuarantine(unreadablePart().receivedRecordId(), note);
    }

    @ならば("取り込めなかった部分の隔離はファイル全体の原本を指す")
    public void ファイル全体の原本を指す() {
        assertThat(received(unreadablePart().receivedRecordId()).payloadObjectKey())
                .isEqualTo(ingestion().fileObjectKey());
    }

    @ならば("取り込めなかった部分の隔離は {int} 行目からファイルの終わりまでの原文を原本として指す")
    public void ファイルの終わりまでの原文を指す(int fromLine) {
        List<String> lines = csv.lines().toList();
        String raw = String.join("\n", lines.subList(fromLine - 1, lines.size()));
        assertStoredPayload(unreadablePart().receivedRecordId(), raw);
    }

    @ならば("行の結果はない")
    public void 行の結果はない() {
        assertThat(result.rows()).isEmpty();
    }

    @ならば("行の結果の分類は物理行の順に {string} である")
    public void 行の結果の分類(String classifications) {
        assertThat(result.rows())
                .extracting(RowResult::classification)
                .containsExactlyElementsOf(Arrays.stream(classifications.split("・"))
                        .map(ExternalRecordFormatErrorSteps::classification)
                        .toList());
    }

    @ならば("{int} 行目の行は外部原本を持たない隔離で、注記は {string} である")
    public void 行の誤りの隔離(int lineNumber, String note) {
        RowResult row = rowAt(lineNumber);
        assertThat(row.classification()).isEqualTo(Classification.QUARANTINED);
        assertThat(row.externalRecordId()).isNull();
        assertKeylessQuarantine(row.receivedRecordId(), note);
    }

    @ならば("{int} 行目の隔離は行の原文 {string} を原本として指す")
    public void 行の原文を指す(int lineNumber, String raw) {
        assertStoredPayload(rowAt(lineNumber).receivedRecordId(), raw);
    }

    @ならば("外部原本は {int} 件である")
    public void 外部原本の件数(int count) {
        assertThat(externalRecords.all()).hasSize(count);
    }

    private void ingest(String provider, String content, Charset charset) {
        csv = content;
        DataSource source = sourceOf(provider);
        IngestionOutcome outcome = ingestionService.ingest(
                DATA_STEWARD,
                IngestFileCommandAssembler.assemble(
                        source.id(), CsvFileKind.valueOf(source.recordType()), content.getBytes(charset)));
        assertThat(outcome).isInstanceOf(Ingested.class);
        result = (Ingested) outcome;
    }

    private DataSource sourceOf(String provider) {
        List<DataSource> found = dataSources.all().stream()
                .filter(source -> source.provider().equals(provider))
                .toList();
        assertThat(found).as("提供元 %s の情報源", provider).hasSize(1);
        return found.getFirst();
    }

    private Ingestion ingestion() {
        return ingestions.all().stream()
                .filter(candidate -> candidate.id().equals(result.ingestionId()))
                .findFirst()
                .orElseThrow();
    }

    private Unreadable unreadablePart() {
        assertThat(result.unreadable()).as("取り込めなかった部分").isNotNull();
        return result.unreadable();
    }

    private RowResult rowAt(int lineNumber) {
        return result.rows().stream()
                .filter(row -> row.lineNumber() == lineNumber)
                .findFirst()
                .orElseThrow(() -> new AssertionError(lineNumber + " 行目の行の結果がない"));
    }

    private ReceivedRecord received(UUID receivedRecordId) {
        return receivedRecords.all().stream()
                .filter(candidate -> candidate.id().equals(receivedRecordId))
                .findFirst()
                .orElseThrow();
    }

    /** 外部原本と取得時刻を持たない隔離の受信記録で、取込に関連付き、注記が業務の言葉であること。 */
    private void assertKeylessQuarantine(UUID receivedRecordId, String note) {
        ReceivedRecord received = received(receivedRecordId);
        assertThat(received.classification()).isEqualTo(Classification.QUARANTINED);
        assertThat(received.externalRecordId()).isNull();
        assertThat(received.acquiredAt()).isNull();
        assertThat(received.ingestionId()).isEqualTo(result.ingestionId());
        assertThat(received.classificationNote()).isEqualTo(note);
    }

    /** 受信記録が指す原本が、その原文の UTF-8 のバイト列として保存されていること。 */
    private void assertStoredPayload(UUID receivedRecordId, String raw) {
        assertThat(payloadStorage.read(received(receivedRecordId).payloadObjectKey()))
                .isEqualTo(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Classification classification(String name) {
        return switch (name) {
            case "採用" -> Classification.ADOPTED;
            case "重複" -> Classification.DUPLICATE;
            case "隔離" -> Classification.QUARANTINED;
            default -> throw new IllegalArgumentException("未知の分類: " + name);
        };
    }
}
