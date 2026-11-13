package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 有人案件のリポジトリ（送信ポート。Bolt 40-6）。作成者・更新者は集約に持たせず、呼び出しの操作者を渡す。
 */
public interface ServiceCaseRepository {

    /** 起票した案件を保存する（子の出典・進捗・回答を含む。版は 0）。 */
    void save(ServiceCase serviceCase, UserId actor);

    /**
     * 更新した案件を保存する。進捗と回答は追記専用で、表にない番号の行だけを足し、既存の回答には顧客の回答（未設定のときだけ）を書く。
     *
     * @throws ConcurrentServiceCaseUpdateException 読み込んだ後にほかの更新が先に保存されていた
     */
    void update(ServiceCase serviceCase, UserId actor);

    /**
     * 同じ荷主企業・予約・内容で未終結（終結・重複終了でない）の案件の受付番号。重複起票を防ぐ（Bolt 40-7）。荷主企業・予約は指定がなければ「ない」案件と比べる。
     */
    Optional<CaseNumber> findUnclosedDuplicate(
            Optional<CompanyId> shipperCompanyId, Optional<UUID> bookingId, String cause);

    /**
     * 受領期限を過ぎた案件（受付済みで期限を厳密に過ぎたものと、上位対応中）を、期限の古い順に返す（Bolt 40-9。S-14 の期限超過と、W10 の通知が使う）。
     *
     * @param now 判定の時刻
     * @param limit 返す最大の件数
     */
    List<ServiceCase> findReceiptOverdue(UtcInstant now, int limit);

    /**
     * 指定した状態の案件を、受領期限の古い順（同じなら受付番号順）に返す（Bolt 40-11。S-14 の未終結・未受領・対応中）。
     *
     * @param statuses 状態の集合（空なら何も返さない）
     * @param limit 返す最大の件数
     */
    List<ServiceCase> findByStatuses(Set<ServiceCaseStatus> statuses, int limit);

    /** 受付番号で案件を読む。 */
    Optional<ServiceCase> findByNumber(CaseNumber number);
}
