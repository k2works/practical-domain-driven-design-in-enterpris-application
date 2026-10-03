package com.example.cargotracker.quotation.domain.model.rules;

import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 書類の受付規則（Q-INV-16、2026-10-03 の D-20）。添付した書類の形式・大きさ・件数を判定し、違反を提出の検証結果の形で返す。
 * 必要書類は任意なので、書類がないことは違反にしない。
 */
public class RequiredDocumentPolicy {

    /** 1 件の大きさの上限（10 MB）。 */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** 種類ごとの件数の上限。合計は 5 件になる。 */
    private static final Map<DocumentType, Integer> LIMITS = new EnumMap<>(Map.of(
            DocumentType.COMMERCIAL_INVOICE, 1,
            DocumentType.PACKING_LIST, 1,
            DocumentType.OTHER, 3));

    /**
     * 引き継いだ書類（出し直しのとき）と足した書類を合わせて判定する。違反はまとめて返し、同じ違反は重ねない。
     *
     * @param carried 前の版から引き継ぐ書類（提出のときは空）
     * @param added 新しく添付した書類
     */
    public List<Violation> check(List<RequiredDocument> carried, List<RequiredDocumentAttachment> added) {
        Set<Violation> violations = new LinkedHashSet<>();
        for (RequiredDocumentAttachment attachment : added) {
            if (attachment.size() > MAX_BYTES) {
                violations.add(new Violation(item(attachment.type()), Reason.TOO_LARGE));
            } else if (DocumentMediaType.detect(attachment.content()).isEmpty()) {
                violations.add(new Violation(item(attachment.type()), Reason.UNSUPPORTED_FORMAT));
            }
        }
        LIMITS.forEach((type, limit) -> {
            long count = Stream.concat(
                            carried.stream().map(RequiredDocument::type),
                            added.stream().map(RequiredDocumentAttachment::type))
                    .filter(type::equals)
                    .count();
            if (count > limit) {
                violations.add(new Violation(item(type), Reason.TOO_MANY));
            }
        });
        return List.copyOf(violations);
    }

    /** 書類の種類を、誤りを示す入力欄（提出の検証の項目）に対応させる。 */
    private static Item item(DocumentType type) {
        return switch (type) {
            case COMMERCIAL_INVOICE -> Item.COMMERCIAL_INVOICE;
            case PACKING_LIST -> Item.PACKING_LIST;
            case OTHER -> Item.OTHER_DOCUMENTS;
        };
    }
}
