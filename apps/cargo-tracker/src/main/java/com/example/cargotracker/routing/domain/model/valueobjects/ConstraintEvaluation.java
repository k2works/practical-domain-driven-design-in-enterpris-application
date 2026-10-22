package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 制約適合判定。期限・接続時間・貨物種別による候補の適合・除外とその理由（R-INV-01・02）。除外理由がなければ適合。
 * 算出の集約が、情報鮮度の判定の理由（情報不足。R-INV-09）を後ろに足す（Bolt 37b2）。
 *
 * @param estimatedArrivalAt 到着予定（最終区間の到着予定）
 * @param reasons 除外理由（空なら適合）
 * @param minConnectionSlack 接続余裕（接続時間 − 必要最小接続時間の最小。直行と、規則のない接続だけの候補は null）
 */
@ValueObject
public record ConstraintEvaluation(
        UtcInstant estimatedArrivalAt, List<ExclusionReason> reasons, Duration minConnectionSlack) {

    public ConstraintEvaluation {
        Objects.requireNonNull(estimatedArrivalAt, "estimatedArrivalAt");
        reasons = List.copyOf(reasons);
    }

    /** 適合か（除外理由がない）。 */
    public boolean conforming() {
        return reasons.isEmpty();
    }

    /** 情報不足か（理由に情報不足がある。保存の {@code info_insufficient}。Bolt 37b2）。 */
    public boolean infoInsufficient() {
        return reasons.stream().anyMatch(reason -> reason.code() == ExclusionReasonCode.INFO_INSUFFICIENT);
    }

    /** 理由を後ろに足した判定（到着予定と接続余裕は変えない。Bolt 37b2）。 */
    public ConstraintEvaluation withAddedReasons(List<ExclusionReason> added) {
        if (added.isEmpty()) {
            return this;
        }
        List<ExclusionReason> all = new ArrayList<>(reasons);
        all.addAll(added);
        return new ConstraintEvaluation(estimatedArrivalAt, all, minConnectionSlack);
    }

    /** 接続余裕。 */
    public Optional<Duration> connectionSlack() {
        return Optional.ofNullable(minConnectionSlack);
    }
}
