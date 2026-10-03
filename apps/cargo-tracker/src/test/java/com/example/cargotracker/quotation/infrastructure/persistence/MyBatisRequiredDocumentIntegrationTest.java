package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 必要書類（`required_document`、Q-INV-16）の永続化を PostgreSQL で確かめる。
 * 業務番号はほかのテストとぶつからないよう 2083 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisRequiredDocumentIntegrationTest {

    private static final UserId SUBMITTER = new UserId(UUID.randomUUID());
    private static final UserId REVIEWER = new UserId(UUID.randomUUID());
    private static final UtcInstant SUBMITTED_AT = new UtcInstant(Instant.parse("2083-01-05T01:00:00Z"));
    private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2083-01-05T03:00:00Z"));

    @Autowired
    TransportRequestRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private static RequiredDocument document(int documentNo, DocumentType type, String fileName) {
        return new RequiredDocument(
                documentNo,
                type,
                fileName,
                DocumentMediaType.PDF,
                1024L * documentNo,
                Integer.toHexString(documentNo).repeat(64).substring(0, 64),
                "quotation/" + UUID.randomUUID() + "/" + UUID.randomUUID());
    }

    private TransportRequest saved(int sequence, List<RequiredDocument> documents) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2083, sequence),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo().withDocuments(documents),
                SUBMITTER,
                SUBMITTED_AT);
        repository.save(request);
        return request;
    }

    @Test
    void 提出した版の書類を書類番号の順に保存し読み出せる() {
        RequiredDocument invoice = document(1, DocumentType.COMMERCIAL_INVOICE, "invoice.pdf");
        RequiredDocument packing = document(2, DocumentType.PACKING_LIST, "packing.pdf");
        TransportRequest request = saved(1, List.of(invoice, packing));

        assertThat(repository.findById(request.id()))
                .hasValueSatisfying(found ->
                        assertThat(found.currentVersion().terms().documents()).containsExactly(invoice, packing));
    }

    @Test
    void 書類のない版は書類の一覧が空になる() {
        TransportRequest request = saved(2, List.of());

        assertThat(repository.findById(request.id()))
                .hasValueSatisfying(found ->
                        assertThat(found.currentVersion().terms().documents()).isEmpty());
    }

    @Test
    void 出し直すと版2に前の版の書類を同じオブジェクトキーで引き継ぎ足した書類を加え版1の書類は変わらない() {
        RequiredDocument invoice = document(1, DocumentType.COMMERCIAL_INVOICE, "invoice.pdf");
        TransportRequest request = saved(3, List.of(invoice));
        request.sendBack(1, REVIEWER, "梱包明細が必要", "梱包明細", DECIDED_AT);
        repository.update(request);
        TransportRequest draft = repository.findById(request.id()).orElseThrow();
        RequiredDocument packing = document(2, DocumentType.PACKING_LIST, "packing.pdf");
        ShipmentTerms terms = draft.currentVersion().terms();
        draft.resubmit(terms.withDocuments(List.of(invoice, packing)), SUBMITTER, DECIDED_AT);

        repository.update(draft);

        assertThat(repository.findById(request.id()))
                .hasValueSatisfying(found ->
                        assertThat(found.currentVersion().terms().documents()).containsExactly(invoice, packing));
        assertThat(jdbc.queryForList(
                        "SELECT version_no, document_no, object_key FROM quotation.required_document"
                                + " WHERE transport_request_id = ? ORDER BY version_no, document_no",
                        request.id().value()))
                .extracting(row -> row.get("version_no") + "-" + row.get("document_no") + "-" + row.get("object_key"))
                .containsExactly(
                        "1-1-" + invoice.objectKey(), "2-1-" + invoice.objectKey(), "2-2-" + packing.objectKey());
    }

    /** PostgreSQL は制約違反でトランザクションを中断するため、違反ごとに別のテストにする。 */
    @ParameterizedTest
    @CsvSource({"UNKNOWN, PDF, 10", "OTHER, GIF, 10", "OTHER, PDF, 10485761", "OTHER, PDF, 0"})
    void 書類の種類と形式と大きさはCHECK制約で守る(String documentType, String mediaType, long sizeBytes) {
        TransportRequest request = saved(4, List.of());
        String insert = "INSERT INTO quotation.required_document (transport_request_id, version_no, document_no,"
                + " document_type, file_name, media_type, size_bytes, sha256, object_key)"
                + " VALUES (?, 1, 1, ?, 'x.pdf', ?, ?, ?, 'quotation/x/y')";

        UUID id = request.id().value();
        String sha256 = "a".repeat(64);

        assertThatThrownBy(() -> jdbc.update(insert, id, documentType, mediaType, sizeBytes, sha256))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
