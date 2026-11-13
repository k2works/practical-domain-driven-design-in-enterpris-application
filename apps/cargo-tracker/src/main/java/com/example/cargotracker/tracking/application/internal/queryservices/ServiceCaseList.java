package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import java.util.List;

/**
 * 有人案件の一覧（S-14）の結果（Bolt 40-11）。
 *
 * @param items 受領期限の古い順の行（上限まで）
 * @param truncated 上限を超える案件があり、打ち切ったか
 * @param limit 上限の件数
 */
@Query
public record ServiceCaseList(List<ServiceCaseListItem> items, boolean truncated, int limit) {

    public ServiceCaseList {
        items = List.copyOf(items);
    }

    /** 行の一覧（呼び出し側が書き換えられない写し）。 */
    @Override
    public List<ServiceCaseListItem> items() {
        return List.copyOf(items);
    }
}
