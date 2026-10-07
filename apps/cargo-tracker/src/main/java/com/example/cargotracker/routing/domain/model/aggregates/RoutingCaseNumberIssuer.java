package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;

/**
 * 案件番号の採番。年を受け取り、その年の次の案件番号を返す（業務番号の採番と同じ規則。D-10。Bolt 17）。
 * 案件の作成と同じトランザクションで呼び、作成が失敗すれば番号も戻るようにする。
 */
public interface RoutingCaseNumberIssuer {

    /** その年の次の案件番号を振る。 */
    RoutingCaseNumber next(int year);
}
