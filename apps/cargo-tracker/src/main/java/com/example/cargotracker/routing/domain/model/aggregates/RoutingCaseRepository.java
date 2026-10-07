package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 経路設計案件のリポジトリ（送信ポート）。経路版・候補・区間・除外理由を案件とともに読み書きする。
 */
public interface RoutingCaseRepository {

    /**
     * 新しい案件を保存する。同じ輸送要求版の案件は保存できない（UK。R-INV-10）。
     *
     * @throws DuplicateRoutingCaseException 同じ輸送要求版の案件が先に保存されていた（DE-16 の同時の再配信）
     */
    void save(RoutingCase routingCase);

    /**
     * 案件を更新する。読み込んだときの集約の版で照合し、いまの経路版の状態と候補を書き直す（確定した経路版の候補は書き直さない）。
     * 確定したら確定した経路版の番号を残す。操作者を案件の最終更新者に残す（Bolt 19）。
     *
     * @throws ConcurrentRoutingCaseUpdateException 読み込んだ後に、ほかの更新が先に保存されていた
     */
    void update(RoutingCase routingCase, UUID operatorId);

    /** 案件番号で探す。 */
    Optional<RoutingCase> findByNumber(RoutingCaseNumber number);

    /** 輸送要求版の案件があるか（DE-16 の再配信で重複して作らないため。R-INV-10）。 */
    boolean existsForTransportRequestVersion(UUID transportRequestId, int transportRequestVersionNo);

    /** 案件一覧（S-05）を、見積有効期限の近い順（期限のない案件は後ろ）、同じなら依頼の古い順に返す（Bolt 19）。 */
    List<RoutingCaseSummary> findSummaries();
}
