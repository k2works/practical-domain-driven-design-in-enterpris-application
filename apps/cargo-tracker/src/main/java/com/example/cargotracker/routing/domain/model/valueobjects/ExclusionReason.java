package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ArrivalDeadline;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ConnectionRuleMissing;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.MinimumConnection;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.StaleVoyageInfo;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.UnsupportedCargo;
import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 除外理由。候補を除外した理由の区分と、不適合となった時刻・閾値・参照情報版（R-INV-02）。
 * 区分は閾値の型で決まる（{@link ExclusionThreshold}。区分と閾値の不一致は作れない。Bolt 39i）。
 *
 * @param threshold 閾値（期限超過は希望到着期限、接続不足は規則の港と必要最小接続時間、接続できないは規則のなかった港、
 *     情報不足は古い航海の航海番号と許容時間、貨物種別は案件の貨物種別）
 * @param violatedAt 不適合となった時刻（期限超過は到着予定、接続不足・接続できないは次の区間の出発予定、情報不足は取得時刻 + 許容時間、
 *     貨物種別は最初の区間の出発予定）
 * @param infoVersion 参照情報版（不適合の時刻を取った航海の採用情報版）
 */
@ValueObject
public record ExclusionReason(ExclusionThreshold threshold, UtcInstant violatedAt, String infoVersion) {

    public ExclusionReason {
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(violatedAt, "violatedAt");
        Objects.requireNonNull(infoVersion, "infoVersion");
    }

    /** 区分（閾値の型で決まる）。 */
    public ExclusionReasonCode code() {
        return threshold.code();
    }

    /** 期限超過。不適合の時刻は到着予定、閾値は希望到着期限。 */
    public static ExclusionReason deadlineExceeded(UtcInstant arrivalAt, UtcInstant deadline, String infoVersion) {
        return new ExclusionReason(new ArrivalDeadline(deadline), arrivalAt, infoVersion);
    }

    /** 接続不足。不適合の時刻は次の区間の出発予定、閾値は規則の港と必要最小接続時間。 */
    public static ExclusionReason connectionTooShort(
            UtcInstant nextDepartureAt, Location port, Duration requiredConnection, String infoVersion) {
        return new ExclusionReason(new MinimumConnection(port, requiredConnection), nextDepartureAt, infoVersion);
    }

    /** 接続できない。積替えの港に、次の区間の出発予定の時刻で有効な接続時間規則がない（D-90）。不適合の時刻は次の区間の出発予定。 */
    public static ExclusionReason notConnectable(UtcInstant nextDepartureAt, Location port, String infoVersion) {
        return new ExclusionReason(new ConnectionRuleMissing(port), nextDepartureAt, infoVersion);
    }

    /**
     * 情報不足。航海の取得時刻から許容時間を過ぎてから算出した（R-INV-09、D-89。Bolt 37b2）。不適合の時刻は情報が古くなった時刻
     * （取得時刻 + 許容時間）、閾値は航海番号と許容時間、参照情報版はその航海の採用情報版（AC4 の最終採用値）。
     */
    public static ExclusionReason infoInsufficient(
            String voyageNumber, UtcInstant acquiredAt, Duration tolerance, String infoVersion) {
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(tolerance, "tolerance");
        return new ExclusionReason(
                new StaleVoyageInfo(voyageNumber, tolerance),
                new UtcInstant(acquiredAt.instant().plus(tolerance)),
                infoVersion);
    }

    /**
     * 貨物種別に対応しない（BR-02、BR-03、Bolt 39i）。不適合の時刻は最初の区間の出発予定、閾値は案件の貨物種別、
     * 参照情報版は最初の区間の航海の採用情報版。
     */
    public static ExclusionReason cargoNotSupported(
            RouteCargoCategory category, UtcInstant firstDepartureAt, String infoVersion) {
        return new ExclusionReason(new UnsupportedCargo(category), firstDepartureAt, infoVersion);
    }

    /** 情報不足の航海の取得時刻（不適合の時刻から許容時間を引く。ほかの区分は空）。 */
    public Optional<UtcInstant> infoAcquiredAt() {
        if (threshold instanceof StaleVoyageInfo stale) {
            return Optional.of(new UtcInstant(violatedAt.instant().minus(stale.tolerance())));
        }
        return Optional.empty();
    }
}
