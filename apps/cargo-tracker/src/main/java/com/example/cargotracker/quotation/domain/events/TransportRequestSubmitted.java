package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-01 輸送要求を提出した。監査と KPI 計測（KPI-01 の開始時刻）が購読する。
 *
 * @param transportRequestId 輸送要求 ID
 * @param versionNo 版番号
 * @param shipperCompanyId 荷主企業
 * @param submittedAt 提出時刻
 */
@DomainEvent
public record TransportRequestSubmitted(
        UUID transportRequestId, int versionNo, CompanyId shipperCompanyId, UtcInstant submittedAt) {}
