package com.example.cargotracker.booking.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-07 本予約を確定した（US-04 AC1。Bolt 23）。予約の listener が見積りの公開 API で輸送要求を予約確定済みにし（ADR-014）、
 * 追跡が購読して追跡を開始する（ADR-015。Bolt 25）。業務キーは予約 ID。イベントはドメインの型を持たない。
 *
 * @param bookingId 予約 ID
 * @param bookingVersionNo 予約版番号
 * @param trackingNumber 追跡番号の表記
 * @param quotationId 見積り ID
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param transportRequestNumber 業務番号の表記
 * @param routingCaseNumber 承認済み経路版の案件番号の表記
 * @param routeVersionNo 承認済み経路版の番号
 * @param committedAt commit 時刻
 * @param aggregateVersion 発行元の集約の版（B-INV-12）
 * @param shipperCompanyId 荷主企業 ID（追跡記録に要る値。Bolt 25 から。Bolt 23・24 に発行したものは null）
 * @param consigneeCompanyId 荷受人企業 ID（同上）
 */
@DomainEvent
public record BookingConfirmed(
        UUID bookingId,
        int bookingVersionNo,
        String trackingNumber,
        UUID quotationId,
        UUID transportRequestId,
        int transportRequestVersionNo,
        String transportRequestNumber,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant committedAt,
        long aggregateVersion,
        CompanyId shipperCompanyId,
        CompanyId consigneeCompanyId) {}
