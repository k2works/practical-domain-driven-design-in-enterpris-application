package com.example.cargotracker.quotation.domain.model.entities;

import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 輸送要求版。輸送要求のある時点の条件。提出後は変更せず、変更は新しい版にする（Q-INV-03）。
 *
 * @param versionNo 版番号
 * @param terms 輸送条件
 * @param submittedBy 提出者
 * @param submittedAt 提出時刻（KPI-01 の開始時刻）
 */
public record TransportRequestVersion(int versionNo, ShipmentTerms terms, UserId submittedBy, UtcInstant submittedAt) {

    public TransportRequestVersion {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
