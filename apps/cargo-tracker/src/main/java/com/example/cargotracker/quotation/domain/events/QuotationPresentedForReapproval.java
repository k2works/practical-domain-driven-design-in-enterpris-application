package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-24 再見積りを荷主の再承認待ちにした（D-85。Bolt 35b2）。荷主の承認の後に失効した見積りの再見積りで、経路を引き継いだ見積りを
 * 社内承認して提示したときに DE-03 の後に出す。見積り（輸送要求を予約待ちから荷主承認待ちへ戻す）が購読する。届く順の入れ替わりの
 * 守りは ADR-018。業務キーは見積り ID。イベントはドメインの型を持たない。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
 * @param presentedAt 提示時刻
 */
@DomainEvent
public record QuotationPresentedForReapproval(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UtcInstant presentedAt) {}
