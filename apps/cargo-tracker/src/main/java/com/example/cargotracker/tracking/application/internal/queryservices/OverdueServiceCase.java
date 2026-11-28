package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.RequesterNotice;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Duration;
import java.util.Objects;

/**
 * 受領期限を過ぎた有人案件の 1 件（S-14 の期限超過の一覧と、W10 の上位の運用責任者への通知が使う。Bolt 40-9）。
 *
 * @param number 受付番号
 * @param kind 種類
 * @param priority 優先度
 * @param department 担当部門
 * @param status 状態（受付済み、または上位対応中）
 * @param receiveDueAt 受領期限
 * @param overdueBy 期限を過ぎた長さ
 * @param requesterNotice 依頼元に示す内容（担当未確定と次回更新日時）
 */
@Query
public record OverdueServiceCase(
        CaseNumber number,
        ServiceCaseKind kind,
        ServicePriority priority,
        String department,
        ServiceCaseStatus status,
        UtcInstant receiveDueAt,
        Duration overdueBy,
        RequesterNotice requesterNotice) {

    public OverdueServiceCase {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(department, "department");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(receiveDueAt, "receiveDueAt");
        Objects.requireNonNull(overdueBy, "overdueBy");
        Objects.requireNonNull(requesterNotice, "requesterNotice");
    }
}
