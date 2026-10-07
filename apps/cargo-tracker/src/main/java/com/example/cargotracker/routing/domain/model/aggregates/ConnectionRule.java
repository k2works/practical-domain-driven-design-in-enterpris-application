package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * 接続時間規則。積替えの港ごとの必要最小接続時間と適用期間（BR-11。集約ルート）。
 * 業務責任者の値（DM-05）が届くまでは、開発環境の仮の値だけを読む（Bolt 17）。航路の範囲（route_scope）は R0.1 では使わない。
 *
 * @param id 規則 ID
 * @param port 対象の港
 * @param minimumConnection 必要最小接続時間
 * @param validFrom 適用の開始（含む）
 * @param validTo 適用の終わり（含まない。null は終わりなし）
 */
@AggregateRoot
public record ConnectionRule(
        UUID id, Location port, Duration minimumConnection, UtcInstant validFrom, UtcInstant validTo) {

    public ConnectionRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(minimumConnection, "minimumConnection");
        Objects.requireNonNull(validFrom, "validFrom");
        if (minimumConnection.isNegative()) {
            throw new IllegalArgumentException("必要最小接続時間が負です: " + minimumConnection);
        }
    }

    /** その港の積替えに、その時刻で適用するか。適用期間は開始を含み、終わりを含まない。 */
    public boolean appliesTo(Location at, UtcInstant judgedAt) {
        return port.equals(at)
                && !judgedAt.instant().isBefore(validFrom.instant())
                && (validTo == null || judgedAt.instant().isBefore(validTo.instant()));
    }
}
