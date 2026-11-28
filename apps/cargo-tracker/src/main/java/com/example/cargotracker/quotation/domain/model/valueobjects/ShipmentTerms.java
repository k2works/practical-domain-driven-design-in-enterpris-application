package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 輸送条件。荷受人、出発地、目的地、希望到着期限、貨物、必要書類（0〜5 件。任意。D-20）の組。
 * 提出の検証（{@link ShipmentTermsInput}）を通った値だけで作る。
 *
 * @param consigneeCompanyId 荷受人企業 ID
 * @param origin 出発地
 * @param destination 目的地（出発地と異なる。D-6）
 * @param arrivalDeadline 希望到着期限
 * @param cargo 貨物
 * @param documents 必要書類（書類番号の順）
 */
@ValueObject
public record ShipmentTerms(
        CompanyId consigneeCompanyId,
        Location origin,
        Location destination,
        UtcInstant arrivalDeadline,
        Cargo cargo,
        List<RequiredDocument> documents) {

    public ShipmentTerms {
        Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        Objects.requireNonNull(cargo, "cargo");
        documents = List.copyOf(documents);
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地が同じです: " + origin.unLocode());
        }
        requireDocumentLimits(documents);
    }

    /** 必要書類のない輸送条件。 */
    public ShipmentTerms(
            CompanyId consigneeCompanyId,
            Location origin,
            Location destination,
            UtcInstant arrivalDeadline,
            Cargo cargo) {
        this(consigneeCompanyId, origin, destination, arrivalDeadline, cargo, List.of());
    }

    /**
     * 種類ごとの件数の上限と、書類番号の一意を守る（Q-INV-16。Bolt 6〜8 レビュー R-29）。
     * 利用者の誤りは書類の受付規則が先に判定するので、ここで破れるのは前提の誤りである。
     */
    private static void requireDocumentLimits(List<RequiredDocument> documents) {
        Map<DocumentType, Long> counts =
                documents.stream().collect(Collectors.groupingBy(RequiredDocument::type, Collectors.counting()));
        counts.forEach((type, count) -> {
            if (count > type.maxPerVersion()) {
                throw new IllegalArgumentException("書類の件数が上限を超えています: " + type + " " + count + " 件");
            }
        });
        long distinctNumbers = documents.stream()
                .mapToInt(RequiredDocument::documentNo)
                .distinct()
                .count();
        if (distinctNumbers != documents.size()) {
            throw new IllegalArgumentException("書類番号が重なっています: " + documents);
        }
    }

    /** 必要書類を置き換えた輸送条件。 */
    public ShipmentTerms withDocuments(List<RequiredDocument> newDocuments) {
        return new ShipmentTerms(consigneeCompanyId, origin, destination, arrivalDeadline, cargo, newDocuments);
    }
}
