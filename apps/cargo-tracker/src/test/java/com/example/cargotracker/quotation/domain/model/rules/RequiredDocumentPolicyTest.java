package com.example.cargotracker.quotation.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 書類の受付規則（Q-INV-16、D-20）。
 */
class RequiredDocumentPolicyTest {

    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private final RequiredDocumentPolicy policy = new RequiredDocumentPolicy();

    private static RequiredDocumentAttachment pdf(DocumentType type, String fileName, int size) {
        byte[] content = Arrays.copyOf("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), size);
        return new RequiredDocumentAttachment(type, fileName, content);
    }

    private static RequiredDocument carried(int documentNo, DocumentType type) {
        return new RequiredDocument(
                documentNo,
                type,
                "carried.pdf",
                DocumentMediaType.PDF,
                100,
                "0".repeat(64),
                "quotation/x/" + documentNo);
    }

    @Test
    void 書類がなくても違反はない() {
        assertThat(policy.check(List.of(), List.of())).isEmpty();
    }

    @Test
    void 種類ごとの上限までの書類は受け付ける() {
        assertThat(policy.check(
                        List.of(),
                        List.of(
                                pdf(DocumentType.COMMERCIAL_INVOICE, "i.pdf", 100),
                                pdf(DocumentType.PACKING_LIST, "p.pdf", 100),
                                pdf(DocumentType.OTHER, "a.pdf", 100),
                                pdf(DocumentType.OTHER, "b.pdf", 100),
                                pdf(DocumentType.OTHER, "c.pdf", 100))))
                .isEmpty();
    }

    @Test
    void 大きさは10MBちょうどまで受け付け1バイトでも超えたら大きすぎる() {
        assertThat(policy.check(List.of(), List.of(pdf(DocumentType.COMMERCIAL_INVOICE, "i.pdf", MAX_BYTES))))
                .isEmpty();
        assertThat(policy.check(List.of(), List.of(pdf(DocumentType.COMMERCIAL_INVOICE, "i.pdf", MAX_BYTES + 1))))
                .containsExactly(new Violation(Item.COMMERCIAL_INVOICE, Reason.TOO_LARGE));
    }

    @Test
    void 拡張子がPDFでも中身が対象外の形式なら受け付けない() {
        RequiredDocumentAttachment text = new RequiredDocumentAttachment(
                DocumentType.PACKING_LIST, "packing.pdf", "plain text".getBytes(StandardCharsets.UTF_8));

        assertThat(policy.check(List.of(), List.of(text)))
                .containsExactly(new Violation(Item.PACKING_LIST, Reason.UNSUPPORTED_FORMAT));
    }

    @Test
    void 空のファイルは形式が対象外とする() {
        RequiredDocumentAttachment empty = new RequiredDocumentAttachment(DocumentType.OTHER, "empty.pdf", new byte[0]);

        assertThat(policy.check(List.of(), List.of(empty)))
                .containsExactly(new Violation(Item.OTHER_DOCUMENTS, Reason.UNSUPPORTED_FORMAT));
    }

    @Test
    void 種類ごとの上限を超えたら多すぎるを1件だけ示す() {
        assertThat(policy.check(
                        List.of(),
                        List.of(
                                pdf(DocumentType.OTHER, "a.pdf", 10),
                                pdf(DocumentType.OTHER, "b.pdf", 10),
                                pdf(DocumentType.OTHER, "c.pdf", 10),
                                pdf(DocumentType.OTHER, "d.pdf", 10),
                                pdf(DocumentType.OTHER, "e.pdf", 10))))
                .containsExactly(new Violation(Item.OTHER_DOCUMENTS, Reason.TOO_MANY));
    }

    @Test
    void 引き継いだ書類と足した書類を合わせて上限を判定する() {
        assertThat(policy.check(
                        List.of(carried(1, DocumentType.COMMERCIAL_INVOICE)),
                        List.of(pdf(DocumentType.COMMERCIAL_INVOICE, "i2.pdf", 10))))
                .containsExactly(new Violation(Item.COMMERCIAL_INVOICE, Reason.TOO_MANY));
        assertThat(policy.check(
                        List.of(carried(1, DocumentType.COMMERCIAL_INVOICE)),
                        List.of(pdf(DocumentType.PACKING_LIST, "p.pdf", 10))))
                .isEmpty();
    }

    @Test
    void 違反はまとめて返し同じ違反は重ねない() {
        RequiredDocumentAttachment text =
                new RequiredDocumentAttachment(DocumentType.OTHER, "x.pdf", "plain".getBytes(StandardCharsets.UTF_8));

        assertThat(policy.check(List.of(), List.of(text, text, pdf(DocumentType.PACKING_LIST, "p.pdf", MAX_BYTES + 1))))
                .containsExactlyInAnyOrder(
                        new Violation(Item.OTHER_DOCUMENTS, Reason.UNSUPPORTED_FORMAT),
                        new Violation(Item.PACKING_LIST, Reason.TOO_LARGE));
    }
}
