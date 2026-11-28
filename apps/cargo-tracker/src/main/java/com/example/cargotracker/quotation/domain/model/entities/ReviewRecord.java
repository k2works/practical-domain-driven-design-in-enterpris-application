package com.example.cargotracker.quotation.domain.model.entities;

import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 審査記録。1 つの版に対する審査の判断の記録で、後から変えない（追記専用）。
 *
 * @param id 審査記録 ID
 * @param versionNo 対象の版番号
 * @param decision 判断
 * @param reviewerId 判断者
 * @param rationale 根拠（確定）または理由（差戻し）
 * @param missingItems 不足事項（差戻しのとき、任意。なければ null）
 * @param decidedAt 判断時刻
 */
@Entity
public record ReviewRecord(
        UUID id,
        int versionNo,
        ReviewDecision decision,
        UserId reviewerId,
        String rationale,
        String missingItems,
        UtcInstant decidedAt) {

    public ReviewRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
