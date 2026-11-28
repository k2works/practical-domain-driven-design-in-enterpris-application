package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.util.Objects;

/**
 * 有人案件の一覧（S-14）の 1 行（Bolt 40-11）。
 *
 * @param number 受付番号
 * @param kind 種類
 * @param priority 優先度
 * @param department 担当部門
 * @param status 状態
 * @param receiveDueAt 受領期限
 * @param receiptOverdue 照会したときに受領期限を過ぎているか（読むときの判定）
 */
@Query
public record ServiceCaseListItem(
        CaseNumber number,
        ServiceCaseKind kind,
        ServicePriority priority,
        String department,
        ServiceCaseStatus status,
        UtcInstant receiveDueAt,
        boolean receiptOverdue) {

    public ServiceCaseListItem {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(department, "department");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(receiveDueAt, "receiveDueAt");
    }
}
