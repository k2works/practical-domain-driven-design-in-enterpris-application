package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-01 輸送要求を提出した。監査と KPI 計測（KPI-01 の開始時刻）が購読する。
 * 業務キーは輸送要求 ID と版番号。業務番号は表示用の写しで、Bolt 4 で足した null を許す部品である
 * （イベントの進化の規則。Bolt 4 より前に保存された DE-01 には業務番号がない）。
 *
 * @param transportRequestId 輸送要求 ID
 * @param versionNo 版番号
 * @param shipperCompanyId 荷主企業
 * @param submittedAt 提出時刻
 * @param transportRequestNumber 業務番号の表記（例: {@code TR-2026-0001}。古いイベントでは null）
 */
@DomainEvent
public record TransportRequestSubmitted(
        UUID transportRequestId,
        int versionNo,
        CompanyId shipperCompanyId,
        UtcInstant submittedAt,
        String transportRequestNumber) {}
