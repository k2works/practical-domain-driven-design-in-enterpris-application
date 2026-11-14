package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * 荷主の問い合わせ一覧（C-14）の 1 行（US-23 AC1。Bolt 42-2）。受付番号・状態・次回更新日時・最新の回答。
 *
 * @param latestAnswer 最新の回答（まだなければ空）
 */
@Query
public record CustomerServiceCaseItem(
        CaseNumber number, ServiceCaseStatus status, UtcInstant nextUpdateAt, Optional<CaseAnswer> latestAnswer) {

    public CustomerServiceCaseItem {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
        Objects.requireNonNull(latestAnswer, "latestAnswer");
    }
}
