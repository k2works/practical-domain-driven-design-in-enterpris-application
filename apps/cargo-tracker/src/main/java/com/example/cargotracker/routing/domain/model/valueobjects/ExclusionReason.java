package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 除外理由。候補を除外した理由の区分と、不適合となった時刻・閾値・参照情報版（R-INV-02）。
 * 閾値は区分で決まる。期限超過は希望到着期限、接続不足は規則の港と必要最小接続時間、接続できないは規則のなかった港、
 * 情報不足は古い航海の航海番号と許容時間（Bolt 37b2）。
 *
 * @param code 区分
 * @param violatedAt 不適合となった時刻（期限超過は到着予定、接続不足・接続できないは次の区間の出発予定、情報不足は取得時刻 + 許容時間）
 * @param deadline 期限超過の閾値の希望到着期限（ほかの区分は null）
 * @param port 接続の閾値の港（期限超過は null）
 * @param requiredConnection 接続不足の閾値の必要最小接続時間（ほかの区分は null）
 * @param infoVersion 参照情報版（不適合の時刻を取った航海の採用情報版）
 * @param voyageNumber 情報不足の閾値の航海番号（ほかの区分は null）
 * @param freshnessTolerance 情報不足の閾値の許容時間（ほかの区分は null）
 */
@ValueObject
public record ExclusionReason(
        ExclusionReasonCode code,
        UtcInstant violatedAt,
        UtcInstant deadline,
        Location port,
        Duration requiredConnection,
        String infoVersion,
        String voyageNumber,
        Duration freshnessTolerance) {

    public ExclusionReason {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(violatedAt, "violatedAt");
        Objects.requireNonNull(infoVersion, "infoVersion");
    }

    /** 期限超過。不適合の時刻は到着予定、閾値は希望到着期限。 */
    public static ExclusionReason deadlineExceeded(UtcInstant arrivalAt, UtcInstant deadline, String infoVersion) {
        return new ExclusionReason(
                ExclusionReasonCode.DEADLINE_EXCEEDED,
                arrivalAt,
                Objects.requireNonNull(deadline, "deadline"),
                null,
                null,
                infoVersion,
                null,
                null);
    }

    /** 接続不足。不適合の時刻は次の区間の出発予定、閾値は規則の港と必要最小接続時間。 */
    public static ExclusionReason connectionTooShort(
            UtcInstant nextDepartureAt, Location port, Duration requiredConnection, String infoVersion) {
        return new ExclusionReason(
                ExclusionReasonCode.CONNECTION_TOO_SHORT,
                nextDepartureAt,
                null,
                Objects.requireNonNull(port, "port"),
                Objects.requireNonNull(requiredConnection, "requiredConnection"),
                infoVersion,
                null,
                null);
    }

    /** 接続できない。積替えの港に、次の区間の出発予定の時刻で有効な接続時間規則がない（D-90）。不適合の時刻は次の区間の出発予定。 */
    public static ExclusionReason notConnectable(UtcInstant nextDepartureAt, Location port, String infoVersion) {
        return new ExclusionReason(
                ExclusionReasonCode.NOT_CONNECTABLE,
                nextDepartureAt,
                null,
                Objects.requireNonNull(port, "port"),
                null,
                infoVersion,
                null,
                null);
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
                ExclusionReasonCode.INFO_INSUFFICIENT,
                new UtcInstant(acquiredAt.instant().plus(tolerance)),
                null,
                null,
                null,
                infoVersion,
                Objects.requireNonNull(voyageNumber, "voyageNumber"),
                tolerance);
    }

    /** 情報不足の航海の取得時刻（不適合の時刻から許容時間を引く。ほかの区分は空）。 */
    public Optional<UtcInstant> infoAcquiredAt() {
        return Optional.ofNullable(freshnessTolerance)
                .map(tolerance -> new UtcInstant(violatedAt.instant().minus(tolerance)));
    }

    /** 期限超過の閾値の希望到着期限。 */
    public Optional<UtcInstant> deadlineThreshold() {
        return Optional.ofNullable(deadline);
    }

    /** 接続の閾値の港。 */
    public Optional<Location> portThreshold() {
        return Optional.ofNullable(port);
    }

    /** 接続不足の閾値の必要最小接続時間。 */
    public Optional<Duration> requiredConnectionThreshold() {
        return Optional.ofNullable(requiredConnection);
    }
}
