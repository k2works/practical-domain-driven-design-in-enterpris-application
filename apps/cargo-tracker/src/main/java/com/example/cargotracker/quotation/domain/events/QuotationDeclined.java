package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * 荷主が提示済みの見積りを辞退した（DE-17。US-24 AC2。W9 の Bolt 48-1。台帳 D-173）。見積りの保存のコミットの後に、輸送要求を辞退で終了に
 * する listener が受ける（2 つの集約を 1 つのトランザクションで更新しない）。値は Java の標準の型と共有カーネルの型だけ。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 辞退した見積りの対象の輸送要求の版番号
 * @param declinedBy 辞退した荷主担当者の利用者 ID
 * @param reason 辞退の理由
 * @param declinedAt 辞退の時刻
 */
@DomainEvent
public record QuotationDeclined(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UUID declinedBy,
        String reason,
        UtcInstant declinedAt) {}
