package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.annotation.ddd.Query;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import java.util.Objects;

/**
 * 有人案件の詳細（S-15）の照会の結果。案件と、照会したときの受領期限の判定の組（Bolt 40-11）。判定は照会サービスが行い、画面の部品は結果を示すだけにする。
 *
 * @param serviceCase 有人案件
 * @param receiptOverdue 照会したときに受領期限を過ぎているか
 */
@Query
public record ServiceCaseDetail(ServiceCase serviceCase, boolean receiptOverdue) {

    public ServiceCaseDetail {
        Objects.requireNonNull(serviceCase, "serviceCase");
    }
}
