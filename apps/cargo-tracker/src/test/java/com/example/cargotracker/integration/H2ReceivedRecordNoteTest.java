package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2 の受信記録の注記と補助文字（Bolt 39b4 の行 9。開始準備の決定 (8)）。ローカル起動・デモ環境と同じ dev プロファイルの H2 で、補助文字
 * 1,000 コードポイント（UTF-16 で 2,000 単位）の注記を持つ受信記録を書いて読める（PostgreSQL の {@code VARCHAR(1000)} は文字で数える）。
 * 1,000 コードポイントを超える注記は取込のサービスが切る。
 *
 * <p>取込のサービス自身のトランザクションをコミットする。確かめる行は自分の受信記録 ID で絞り、情報源は最後に停止中にして承認済みのまま
 * 残さない（取込が参照するので消せない。前例 {@link H2EventPublicationCapacityTest}）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2ReceivedRecordNoteTest {

    /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
    private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    @Autowired
    IngestionService ingestionService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 行9_H2で補助文字の注記を持つ受信記録を書いて読め1000コードポイントを超える分はサービスが切る() {
        UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID());
        try {
            UUID receivedRecordId = ingestFileDefect(sourceId, SUPPLEMENTARY.repeat(1_001));

            String note = noteOf(receivedRecordId);
            assertThat(note.codePointCount(0, note.length())).isEqualTo(1_000);
            assertThat(note.length()).isGreaterThan(1_900);
            assertThat(note).startsWith("ファイル: 必要な列がありません（" + SUPPLEMENTARY).endsWith("…");
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行9_H2の注記の列は補助文字だけの1000コードポイントを受けて同じ値を返す() {
        UUID sourceId = insertApprovedSource("運送会社-" + UUID.randomUUID());
        try {
            UUID receivedRecordId = ingestFileDefect(sourceId, "x");
            String note = SUPPLEMENTARY.repeat(1_000);

            jdbc.update(
                    "UPDATE external_data.received_record SET classification_note = ? WHERE id = ?",
                    note,
                    receivedRecordId);

            assertThat(noteOf(receivedRecordId)).isEqualTo(note);
        } finally {
            removeSource(sourceId);
        }
    }

    /** ファイルの誤り（必要な列がない）だけのファイルを取り込み、キーのない隔離の受信記録 ID を返す。注記は列の名前を含む。 */
    private UUID ingestFileDefect(UUID sourceId, String missingColumn) {
        IngestionOutcome outcome = ingestionService.ingest(
                steward,
                new IngestFileCommand(
                        sourceId,
                        "x".getBytes(StandardCharsets.UTF_8),
                        0,
                        List.of(),
                        List.of(),
                        new UnreadablePart(null, 0, FormatDefect.MISSING_COLUMN, List.of(missingColumn), null)));
        assertThat(outcome).isInstanceOf(Ingested.class);
        return ((Ingested) outcome).unreadable().receivedRecordId();
    }

    private String noteOf(UUID receivedRecordId) {
        return jdbc.queryForObject(
                "SELECT classification_note FROM external_data.received_record WHERE id = ?",
                String.class,
                receivedRecordId);
    }

    private UUID insertApprovedSource(String provider) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'MILESTONE', 'APPROVED', 0)",
                id,
                provider);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない。取込が残っていれば消せないので、先に停止中にする。 */
    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update(
                "DELETE FROM external_data.data_source WHERE id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                sourceId,
                sourceId);
    }
}
