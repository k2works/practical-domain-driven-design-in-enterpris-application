package com.example.cargotracker.quotation.domain.model.rules;

import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 書類の受付規則（Q-INV-16、2026-10-03 の D-20）。添付した書類の形式・大きさ・件数を判定し、違反を提出の検証結果の形で返す。
 * 必要書類は任意なので、書類がないことは違反にしない。
 *
 * <p>出し直しでは、添付した種類の前の版の書類を引き継がず（差し替え）、添付しなかった種類の書類を引き継ぐ（2026-10-05 の D-25）。
 * 引き継ぐ書類と添付を合わせて件数を判定する。
 */
@DomainRule
public class RequiredDocumentPolicy {

    /** 1 件の大きさの上限（10 MB）。 */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * 前の版の書類（出し直しのとき）と添付を判定する。違反はまとめて返し、同じ違反は重ねない。
     *
     * @param previous 前の版の書類（提出のときは空）
     * @param added 新しく添付した書類
     */
    public DocumentCheck check(List<RequiredDocument> previous, List<RequiredDocumentAttachment> added) {
        Set<DocumentType> replacedTypes = EnumSet.noneOf(DocumentType.class);
        added.forEach(attachment -> replacedTypes.add(attachment.type()));
        List<RequiredDocument> carried = previous.stream()
                .filter(document -> !replacedTypes.contains(document.type()))
                .toList();

        Set<Violation> violations = new LinkedHashSet<>();
        List<AcceptedAttachment> accepted = new ArrayList<>();
        for (RequiredDocumentAttachment attachment : added) {
            Optional<DocumentMediaType> mediaType = attachment.detectMediaType();
            if (attachment.size() > MAX_BYTES) {
                violations.add(new Violation(item(attachment.type()), Reason.TOO_LARGE));
            } else if (mediaType.isEmpty()) {
                violations.add(new Violation(item(attachment.type()), Reason.UNSUPPORTED_FORMAT));
            } else {
                accepted.add(new AcceptedAttachment(attachment, mediaType.get()));
            }
        }
        for (DocumentType type : DocumentType.values()) {
            long count = Stream.concat(
                            carried.stream().map(RequiredDocument::type),
                            added.stream().map(RequiredDocumentAttachment::type))
                    .filter(type::equals)
                    .count();
            if (count > type.maxPerVersion()) {
                violations.add(new Violation(item(type), Reason.TOO_MANY));
            }
        }
        return new DocumentCheck(carried, accepted, List.copyOf(violations));
    }

    /** 書類の種類を、誤りを示す入力欄（提出の検証の項目）に対応させる。 */
    private static Item item(DocumentType type) {
        return switch (type) {
            case COMMERCIAL_INVOICE -> Item.COMMERCIAL_INVOICE;
            case PACKING_LIST -> Item.PACKING_LIST;
            case OTHER -> Item.OTHER_DOCUMENTS;
        };
    }

    /**
     * 書類の判定の結果。違反がなければ、引き継ぐ書類と受け付けた添付から新しい版の書類を作れる。
     *
     * @param carried 前の版から引き継ぐ書類（添付しなかった種類の書類。前の版の順）
     * @param accepted 形式と大きさの判定を通った添付（添付した順）
     * @param violations 違反（空なら受け付ける）
     */
    public record DocumentCheck(
            List<RequiredDocument> carried, List<AcceptedAttachment> accepted, List<Violation> violations) {

        public DocumentCheck {
            carried = List.copyOf(carried);
            accepted = List.copyOf(accepted);
            violations = List.copyOf(violations);
        }
    }

    /**
     * 形式と大きさの判定を通った添付。判定した形式を持つので、受け取った側は形式を判定し直さない（Bolt 6〜8 レビュー R-11）。
     *
     * @param attachment 添付
     * @param mediaType 中身から判定した形式
     */
    public record AcceptedAttachment(RequiredDocumentAttachment attachment, DocumentMediaType mediaType) {

        public AcceptedAttachment {
            Objects.requireNonNull(attachment, "attachment");
            Objects.requireNonNull(mediaType, "mediaType");
        }
    }
}
