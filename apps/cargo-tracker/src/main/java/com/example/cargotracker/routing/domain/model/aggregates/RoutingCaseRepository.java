package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * 操作者を案件の最終更新者に残す（Bolt 19）。操作者は必須で、システムの操作は {@link #updateBySystem} を使う（Bolt 39f の決定 (7)）。
     *
     * @throws ConcurrentRoutingCaseUpdateException 読み込んだ後に、ほかの更新が先に保存されていた
     * @throws NullPointerException 操作者が null
     */
    void update(RoutingCase routingCase, UUID operatorId);

    /**
     * システムの操作（確定した経路の評価）で案件を更新する。最終更新者を変えず、案件の版を進める（Bolt 37d2。Bolt 39f の決定 (7) で操作を分けた）。
     *
     * @throws ConcurrentRoutingCaseUpdateException 読み込んだ後に、ほかの更新が先に保存されていた
     */
    void updateBySystem(RoutingCase routingCase);

    /** 案件番号で探す。 */
    Optional<RoutingCase> findByNumber(RoutingCaseNumber number);

    /**
     * DE-05（経路を確定した）を受けて見積りに経路の割当てを伝えるために、案件番号で探し、案件の行を呼び手のトランザクションが終わるまで押さえる（DE-05 の listener が評価の
     * 保存と直列にする。US-07 AC5。Bolt 37e2。名前は目的の名前にそろえた。Bolt 37e4c）。呼び手のトランザクションの中でだけ使う。
     */
    Optional<RoutingCase> findByNumberForRouteAssignment(RoutingCaseNumber number);

    /**
     * 最新の経路版が確定で、確定の候補の区間にその航海がある案件の番号を、案件番号の順に返す（航海の更新の評価の対象。Bolt 39f の決定 (2)）。同じ案件は 1 件。
     * 旧版・再設計要・候補提示済みの案件と、確定でない候補の区間だけにある航海は引かない。
     */
    List<RoutingCaseNumber> findCaseNumbersConfirmedOnVoyage(String voyageNumber);

    /** 輸送要求版の案件があるか（DE-16 の再配信で重複して作らないため。R-INV-10）。 */
    boolean existsForTransportRequestVersion(UUID transportRequestId, int transportRequestVersionNo);

    /**
     * 荷主企業の集合の案件一覧（S-05）を、見積有効期限の近い順（期限のない案件は後ろ）、同じなら依頼の古い順に返す（Bolt 19）。
     * 社内の画面は担当企業の集合で絞る（Bolt 38g。全件の口は置かない）。集合が空なら問い合わせずに空を返す。
     */
    List<RoutingCaseSummary> findSummaries(Set<CompanyId> shipperCompanyIds);
}
