package com.example.cargotracker.tracking.application.internal.queryservices;

import java.util.Objects;

/**
 * 荷主担当者が問い合わせ（有人案件）の詳細を開いた結果（US-23。Bolt 42）。他社の案件（{@link OutOfScope}）とない案件（{@link NotFound}）は、
 * 画面にはどちらも同じ「見つからない」で応えて存在を明かすが、他社の案件の試行だけは記録する（AC3）ので、サービスは区別して返す。
 */
public sealed interface CustomerServiceCaseLookup {

    /** 自社の案件。 */
    record Found(ServiceCaseDetail detail) implements CustomerServiceCaseLookup {
        public Found {
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** 他社の案件（内容は返さない。試行を記録する）。 */
    record OutOfScope() implements CustomerServiceCaseLookup {}

    /** ない案件、または荷主担当者でない。 */
    record NotFound() implements CustomerServiceCaseLookup {}
}
