package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Optional;

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

    /** 受付番号で案件を読む。 */
    Optional<ServiceCase> findByNumber(CaseNumber number);
}
