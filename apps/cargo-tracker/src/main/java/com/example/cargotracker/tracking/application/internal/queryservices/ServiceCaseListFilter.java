package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 有人案件の一覧（S-14）の絞り込み（Bolt 40-11。US-20）。URL の値は {@link #parameter()}。
 */
public enum ServiceCaseListFilter {
    /** 未終結（受付済み・上位対応中・対応中・回答済み）。既定。 */
    OPEN(
            "open",
            Set.of(
                    ServiceCaseStatus.RECEIVED,
                    ServiceCaseStatus.ESCALATED,
                    ServiceCaseStatus.IN_PROGRESS,
                    ServiceCaseStatus.ANSWERED)),
    /** 未受領（受付済み・上位対応中）。 */
    UNRECEIVED("unreceived", Set.of(ServiceCaseStatus.RECEIVED, ServiceCaseStatus.ESCALATED)),
    /** 期限超過（受領期限を過ぎた受付済みと上位対応中）。状態の集合ではなく読むときの判定で決める。 */
    OVERDUE("overdue", Set.of()),
    /** 対応中。 */
    IN_PROGRESS("in-progress", Set.of(ServiceCaseStatus.IN_PROGRESS));

    private final String parameter;
    private final Set<ServiceCaseStatus> statuses;

    ServiceCaseListFilter(String parameter, Set<ServiceCaseStatus> statuses) {
        this.parameter = parameter;
        this.statuses = statuses;
    }

    /** URL の絞り込みの値。 */
    public String parameter() {
        return parameter;
    }

    /** この絞り込みの状態の集合（期限超過は空）。 */
    Set<ServiceCaseStatus> statuses() {
        return statuses;
    }

    /** URL の値から絞り込みを引く。未指定は既定（未終結）、知らない値は空。 */
    public static Optional<ServiceCaseListFilter> fromParameter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(OPEN);
        }
        return Arrays.stream(values()).filter(f -> f.parameter.equals(value)).findFirst();
    }
}
