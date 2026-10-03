package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

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

    /** 必要書類を置き換えた輸送条件。 */
    public ShipmentTerms withDocuments(List<RequiredDocument> newDocuments) {
        return new ShipmentTerms(consigneeCompanyId, origin, destination, arrivalDeadline, cargo, newDocuments);
    }
}
