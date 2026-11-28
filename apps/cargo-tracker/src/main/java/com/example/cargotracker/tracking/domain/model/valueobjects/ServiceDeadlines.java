package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * 受領・回答期限。有人案件の受領期限と回答期限（Bolt 40-4。SC-INV-04）で、数え始めは、営業時間内ならその時刻、営業時間外なら次に開く時刻（SVC-03）。
 *
 * @param countedFrom 数え始め
 * @param receiptDue 受領期限
 * @param responseDue 回答期限
 */
@ValueObject
public record ServiceDeadlines(Instant countedFrom, Instant receiptDue, Instant responseDue) {

    public ServiceDeadlines {
        Objects.requireNonNull(countedFrom, "countedFrom");
        Objects.requireNonNull(receiptDue, "receiptDue");
        Objects.requireNonNull(responseDue, "responseDue");
    }

    /** 依頼元に示す次回更新日時。受領期限（営業時間外の優先度高は翌営業日の 10:00。SVC-05）。 */
    public Instant nextUpdateAt() {
        return receiptDue;
    }
}
