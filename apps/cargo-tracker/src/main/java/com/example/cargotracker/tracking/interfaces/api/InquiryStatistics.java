package com.example.cargotracker.tracking.interfaces.api;

import java.time.Instant;

/**
 * 期間の照会の件数（追跡の公開 API。KPI-02 の算式の材料。US-21 AC4。W9 の Bolt 44-1）。アクセス・監査が週次に引く。
 */
public interface InquiryStatistics {

    /**
     * 受付時刻が範囲にある照会の件数。
     *
     * @param fromInclusive この時刻以後
     * @param toExclusive この時刻より前
     * @return 照会総数と、顧客 Web で完結した照会数
     */
    InquiryCounts countBetween(Instant fromInclusive, Instant toExclusive);
}
