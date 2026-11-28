package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.Source;

/**
 * 拒否した主要実績のリポジトリ（送信ポート。Bolt 39g）。
 */
public interface RejectedMilestoneRepository {

    /** 同じ出典（種類と参照）の拒否の記録があるか（DE-13 の再配信の冪等。判定の順の最初）。 */
    boolean existsBySource(Source source);

    /** まだなければ拒否の記録を追加する。同じ出典の記録が同時に書かれていれば、既存とみなして何もしない。 */
    void saveIfAbsent(RejectedMilestone rejected);
}
