package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * 荷主が提示済みの見積りの条件を相談したい（US-24 AC3。W9 の Bolt 46-1）。見積りの状態は変えない。追跡のコンテキストが購読し、相談内容を伴う
 * 有人案件（種類: 見積り相談）を起票する（ADR-014 の方向。見積りは追跡に依存しない）。値は Java の標準の型と共有カーネルの型だけ。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestNumber 業務番号の表記
 * @param shipperCompanyId 荷主企業 ID
 * @param requestedBy 相談した荷主担当者の利用者 ID
 * @param content 相談内容（前後の空白を除いた 1〜500 文字）
 * @param requestedAt 相談の時刻
 */
@DomainEvent
public record QuotationConsultationRequested(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        String transportRequestNumber,
        UUID shipperCompanyId,
        UUID requestedBy,
        String content,
        UtcInstant requestedAt) {}
