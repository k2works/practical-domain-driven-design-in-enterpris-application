package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;

/**
 * 有人案件を起票したときに、その前の顧客 Web の照会を有人対応へ移ったと更新する出口（IR-INV-01。KPI-02。W9 の Bolt 44）。有人案件の起票の共通の
 * 手順（{@code ServiceCaseIntake}）が、保存の後に呼ぶ。起票と同じトランザクションの中で動く。
 */
@FunctionalInterface
public interface InquiryEscalation {

    /** 何もしない（照会記録を使わない組み立て。起票のテストなどが使う）。 */
    InquiryEscalation NONE = (draft, number, openedAt) -> {};

    /**
     * 有人案件を起票した。
     *
     * @param draft 起票の下書き
     * @param number 起票した案件の受付番号
     * @param openedAt 起票した時刻
     */
    void onCaseOpened(ServiceCaseDraft draft, CaseNumber number, UtcInstant openedAt);
}
