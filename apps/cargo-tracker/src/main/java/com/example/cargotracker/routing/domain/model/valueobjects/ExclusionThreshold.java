package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;

/**
 * 除外の閾値。除外の理由の区分ごとに決まる閾値（R-INV-02）。区分と閾値の項目が型で一致する（Bolt 39i）。
 * 期限超過は希望到着期限（{@link ArrivalDeadline}）、接続不足は規則の港と必要最小接続時間（{@link MinimumConnection}）、
 * 接続できないは規則のなかった港（{@link ConnectionRuleMissing}）、情報不足は古い航海の航海番号と許容時間（{@link StaleVoyageInfo}）、
 * 貨物種別は案件の貨物種別（{@link UnsupportedCargo}）。
 */
@ValueObject
public sealed interface ExclusionThreshold {

    /** 閾値が表す除外の理由の区分。 */
    ExclusionReasonCode code();

    /**
     * 期限超過の閾値。
     *
     * @param deadline 希望到着期限
     */
    record ArrivalDeadline(UtcInstant deadline) implements ExclusionThreshold {
        public ArrivalDeadline {
            Objects.requireNonNull(deadline, "deadline");
        }

        @Override
        public ExclusionReasonCode code() {
            return ExclusionReasonCode.DEADLINE_EXCEEDED;
        }
    }

    /**
     * 接続不足の閾値。
     *
     * @param port 規則の港
     * @param required 必要最小接続時間
     */
    record MinimumConnection(Location port, Duration required) implements ExclusionThreshold {
        public MinimumConnection {
            Objects.requireNonNull(port, "port");
            Objects.requireNonNull(required, "required");
        }

        @Override
        public ExclusionReasonCode code() {
            return ExclusionReasonCode.CONNECTION_TOO_SHORT;
        }
    }

    /**
     * 接続できないの閾値（D-90）。
     *
     * @param port 次の区間の出発予定の時刻で有効な接続時間規則がなかった港
     */
    record ConnectionRuleMissing(Location port) implements ExclusionThreshold {
        public ConnectionRuleMissing {
            Objects.requireNonNull(port, "port");
        }

        @Override
        public ExclusionReasonCode code() {
            return ExclusionReasonCode.NOT_CONNECTABLE;
        }
    }

    /**
     * 情報不足の閾値（R-INV-09、D-89）。
     *
     * @param voyageNumber 古い航海の航海番号（空白を含み得る）
     * @param tolerance 許容時間
     */
    record StaleVoyageInfo(String voyageNumber, Duration tolerance) implements ExclusionThreshold {
        public StaleVoyageInfo {
            Objects.requireNonNull(voyageNumber, "voyageNumber");
            Objects.requireNonNull(tolerance, "tolerance");
            if (voyageNumber.isBlank()) {
                throw new IllegalArgumentException("航海番号が空です");
            }
        }

        @Override
        public ExclusionReasonCode code() {
            return ExclusionReasonCode.INFO_INSUFFICIENT;
        }
    }

    /**
     * 貨物種別に対応しないの閾値（BR-02、BR-03）。
     *
     * @param category 案件の貨物種別
     */
    record UnsupportedCargo(RouteCargoCategory category) implements ExclusionThreshold {
        public UnsupportedCargo {
            Objects.requireNonNull(category, "category");
        }

        @Override
        public ExclusionReasonCode code() {
            return ExclusionReasonCode.CARGO_NOT_SUPPORTED;
        }
    }
}
