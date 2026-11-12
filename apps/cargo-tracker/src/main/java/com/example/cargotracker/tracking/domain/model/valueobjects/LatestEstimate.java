package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 最新の到着見込み。時刻と、その見込みの根拠（T-INV-10。Bolt 39g3、根拠は Bolt 39g4c）。最新の到着見込みの規則が返す値は必ず根拠を持ち、追跡記録は見込みと根拠を
 * この 1 つの値で持って受け渡す（片方だけを差し替える誤りを型で防ぐ。前例 {@link DerivedStatus}）。根拠が空なのは Bolt 39g4c の前に見込みを決めた追跡記録だけ。
 *
 * @param at 最新の到着見込みの時刻
 * @param basis 見込みの根拠（Bolt 39g4c の前の追跡記録は空）
 */
@ValueObject
public record LatestEstimate(UtcInstant at, Optional<LatestEstimateBasis> basis) {

    public LatestEstimate {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(basis, "basis");
    }

    /** 根拠を持つ見込み（規則が返す形）。 */
    public static LatestEstimate of(UtcInstant at, LatestEstimateBasis basis) {
        return new LatestEstimate(at, Optional.of(basis));
    }
}
