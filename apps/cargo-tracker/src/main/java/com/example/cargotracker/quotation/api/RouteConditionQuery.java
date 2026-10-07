package com.example.cargotracker.quotation.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 経路条件の照会（見積りの公開 API。Bolt 17）。経路設計が、詳細経路設計の依頼（DE-16）を受けて経路設計案件を作るときに、
 * 依頼の対象の輸送要求版の出発地・目的地・希望到着期限・貨物種別を問い合わせる。社内の照会で、荷主企業で絞らない。
 */
public interface RouteConditionQuery {

    /**
     * 輸送要求版の経路条件を返す。輸送要求がないか、その版が現在の版でなければ空（依頼の後に輸送要求が再提出された）。
     *
     * @param transportRequestId 輸送要求 ID
     * @param transportRequestVersionNo 輸送要求の版番号
     */
    Optional<RouteConditionView> find(UUID transportRequestId, int transportRequestVersionNo);
}
