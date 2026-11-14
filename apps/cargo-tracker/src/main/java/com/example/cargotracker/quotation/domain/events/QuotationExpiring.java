package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * 承認待ちの見積りの有効期限が近い（有効期限の 24 時間前の窓に入った。US-22 AC2。W9 の Bolt 43-2）。1 時間ごとの検出が発行する。荷主担当者への通知の宛先を
 * identity が引くため、荷主企業の ID と業務番号を持つ。値は Java の標準の型と共有カーネルの型だけ。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param expiresAt 有効期限
 * @param shipperCompanyId 荷主企業 ID
 * @param transportRequestNumber 業務番号の表記
 */
@DomainEvent
public record QuotationExpiring(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        UtcInstant expiresAt,
        UUID shipperCompanyId,
        String transportRequestNumber) {}
