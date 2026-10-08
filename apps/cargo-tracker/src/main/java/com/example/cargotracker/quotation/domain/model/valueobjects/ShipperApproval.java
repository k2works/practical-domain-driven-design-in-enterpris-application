package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 荷主承認。荷主担当者が見積りと割り当てた経路を確かめて与えた承認（BR-01、US-24 AC4。Bolt 20）。承認した経路版は、見積りの割り当てた経路。
 *
 * @param approvedBy 承認した荷主担当者
 * @param approvedAt 承認時刻
 */
@ValueObject
public record ShipperApproval(UserId approvedBy, UtcInstant approvedAt) {

    public ShipperApproval {
        Objects.requireNonNull(approvedBy, "approvedBy");
        Objects.requireNonNull(approvedAt, "approvedAt");
    }
}
