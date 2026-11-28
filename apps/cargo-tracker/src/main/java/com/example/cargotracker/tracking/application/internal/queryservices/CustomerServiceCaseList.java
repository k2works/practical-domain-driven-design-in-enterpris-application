package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import java.util.List;

/**
 * 荷主の問い合わせ一覧（C-14）の結果（Bolt 42-2）。新しい順で、上限を超えるときは打ち切りを示す。
 *
 * @param truncated 上限で打ち切ったか
 * @param limit 件数の上限
 */
@Query
public record CustomerServiceCaseList(List<CustomerServiceCaseItem> items, boolean truncated, int limit) {

    public CustomerServiceCaseList {
        items = List.copyOf(items);
    }
}
