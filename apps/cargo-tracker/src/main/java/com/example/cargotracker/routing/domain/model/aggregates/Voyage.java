package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.events.VoyageUpdated;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 航海。航海番号で識別される運航予定。外部原本から採用した値を持つ（集約ルート）。寄港は運航の順に並ぶ。
 * 取り込んで採用した値（DE-12）で書き換わる（Bolt 39e）。取込を経ない仮の航海（開発環境の見本）は公開した時刻を持たない。
 * 等しさは今の 4 項目（航海番号・寄港・採用情報版・取得時刻）で比べる（出典・公開した時刻・集約の版は比べない）。
 */
@AggregateRoot
public final class Voyage {

    private static final long INITIAL_AGGREGATE_VERSION = 0;
    private static final String PROVISIONAL_SOURCE_REFERENCE = "取込を経ない仮の航海";
    private static final int MIN_PORT_CALLS = 2;
    private static final Comparator<Freshness> BY_FRESHNESS = Comparator.comparing(Freshness::publishedAt)
            .thenComparing(Freshness::acquiredAt)
            .thenComparing(Freshness::receivedRecordId);

    private final String voyageNumber;
    private final long aggregateVersion;
    private List<PortCall> portCalls;
    private String adoptedInfoVersion;
    private Source source;
    private UtcInstant publishedAt;

    /**
     * 取込を経ない仮の航海を作る（出典は手入力、公開した時刻なし、版 0）。
     *
     * @param voyageNumber 航海番号
     * @param portCalls 寄港（運航の順。2 つ以上）
     * @param adoptedInfoVersion 採用情報版
     * @param acquiredAt 情報の取得時刻
     */
    public Voyage(String voyageNumber, List<PortCall> portCalls, String adoptedInfoVersion, UtcInstant acquiredAt) {
        this(
                voyageNumber,
                portCalls,
                adoptedInfoVersion,
                new Source(SourceKind.MANUAL_ENTRY, PROVISIONAL_SOURCE_REFERENCE, acquiredAt),
                null,
                INITIAL_AGGREGATE_VERSION);
    }

    private Voyage(
            String voyageNumber,
            List<PortCall> portCalls,
            String adoptedInfoVersion,
            Source source,
            UtcInstant publishedAt,
            long aggregateVersion) {
        this.voyageNumber = Objects.requireNonNull(voyageNumber, "voyageNumber");
        this.adoptedInfoVersion = Objects.requireNonNull(adoptedInfoVersion, "adoptedInfoVersion");
        this.source = Objects.requireNonNull(source, "source");
        this.portCalls = validated(voyageNumber, portCalls);
        this.publishedAt = publishedAt;
        this.aggregateVersion = aggregateVersion;
    }

    /** 初めての DE-12 から航海を作る（版 0）。 */
    public static Voyage adoptedFrom(VoyageAdoption adoption) {
        return new Voyage(
                adoption.voyageNumber(),
                adoption.portCalls(),
                adoption.infoVersion(),
                adoption.source(),
                adoption.publishedAt(),
                INITIAL_AGGREGATE_VERSION);
    }

    /** 保存されている状態から航海を組み立てる（リポジトリが使う）。 */
    public static Voyage reconstitute(
            String voyageNumber,
            List<PortCall> portCalls,
            String adoptedInfoVersion,
            Source source,
            UtcInstant publishedAt,
            long aggregateVersion) {
        return new Voyage(voyageNumber, portCalls, adoptedInfoVersion, source, publishedAt, aggregateVersion);
    }

    /**
     * 今の値より厳密に新しい採用値なら値を置き換える（【業務の判断】(r)）。比べる順は公開した時刻、取得時刻、受信記録 ID（出典の参照）で、
     * 情報版は順序が分からない文字列なので比べない。外部版をまたいでも最後に公開した採用値が効く。公開した時刻のない仮の航海はどの採用値より古い。
     * 配信は少なくとも 1 回なので、完全に同じ採用値は適用しない（冪等）。
     *
     * @return 適用したら真
     */
    public boolean apply(VoyageAdoption adoption) {
        if (publishedAt != null
                && BY_FRESHNESS.compare(
                                freshnessOf(adoption.publishedAt(), adoption.source()),
                                freshnessOf(publishedAt, source))
                        <= 0) {
            return false;
        }
        portCalls = adoption.portCalls();
        adoptedInfoVersion = adoption.infoVersion();
        source = adoption.source();
        publishedAt = adoption.publishedAt();
        return true;
    }

    /** DE-30 航海を更新した（{@link #apply} が適用したときに、保存の後で発行する。Bolt 39f）。 */
    public VoyageUpdated updated() {
        return new VoyageUpdated(voyageNumber, adoptedInfoVersion, publishedAt);
    }

    /** 採用値の新しさを比べる値。 */
    private record Freshness(Instant publishedAt, Instant acquiredAt, String receivedRecordId) {}

    private static Freshness freshnessOf(UtcInstant publishedAt, Source source) {
        return new Freshness(publishedAt.instant(), source.acquiredAt().instant(), source.reference());
    }

    private static List<PortCall> validated(String voyageNumber, List<PortCall> portCalls) {
        List<PortCall> copied = List.copyOf(portCalls);
        if (copied.size() < MIN_PORT_CALLS) {
            throw new IllegalArgumentException("航海には 2 つ以上の寄港が要ります: " + voyageNumber);
        }
        return copied;
    }

    /**
     * 区間が今の航海と一致するか（R-INV-03、【業務の判断】(s)。Bolt 39e2）。航海番号と情報版が同じで、区間の出発予定と到着予定に一致する積地と揚地の寄港の組が 1 つ以上あるときに
     * 真になる。同じ情報版で値の違う DE-12 が適用されるので（【業務の判断】(r)）、情報版だけでは区間の予定の変更を見落とす。一致する組が 2 つ以上（同じ港に同じ時刻で 2 度寄る）のときは、
     * 区間の寄港の位置の組がそのうちにあることも求める（位置を持たない Bolt 39f より前の区間は求めない。【前の決定の変更】(5)）。一致する組が 1 つなら、位置がずれていても一致する。
     *
     * @return 区間の情報版と予定の組が今の航海にあれば真
     */
    public boolean matchesLeg(Leg leg) {
        if (!voyageNumber.equals(leg.voyageNumber()) || !adoptedInfoVersion.equals(leg.infoVersion())) {
            return false;
        }
        List<CallPair> matching = callPairs(leg.load(), leg.discharge()).stream()
                .filter(pair -> pair.hasScheduleOf(leg))
                .toList();
        if (matching.size() < 2 || !leg.hasCallPositions()) {
            return !matching.isEmpty();
        }
        return matching.stream().anyMatch(pair -> pair.isAtPositionsOf(leg));
    }

    /**
     * 港 {@code load} を出発する寄港と、その後で港 {@code discharge} に到着する寄港の組（確定の照合と引き直しが使う。Bolt 39f）。出発のない寄港は積地に、到着のない寄港は揚地にならない。
     *
     * @return 運航の順の組
     */
    public List<CallPair> callPairs(Location load, Location discharge) {
        List<CallPair> pairs = new ArrayList<>();
        for (int from = 0; from < portCalls.size(); from++) {
            PortCall loading = portCalls.get(from);
            if (!loading.port().equals(load) || loading.departureAt() == null) {
                continue;
            }
            for (int to = from + 1; to < portCalls.size(); to++) {
                PortCall discharging = portCalls.get(to);
                if (discharging.port().equals(discharge) && discharging.arrivalAt() != null) {
                    pairs.add(new CallPair(from + 1, to + 1, loading, discharging));
                }
            }
        }
        return List.copyOf(pairs);
    }

    /**
     * 積地の寄港と揚地の寄港の組。
     *
     * @param loadCallNo 積地の寄港の位置（運航の順で 1 始まり）
     * @param dischargeCallNo 揚地の寄港の位置
     * @param load 積地の寄港（出発予定がある）
     * @param discharge 揚地の寄港（到着予定がある）
     */
    public record CallPair(int loadCallNo, int dischargeCallNo, PortCall load, PortCall discharge) {

        /** 出発予定と到着予定が区間と同じか。 */
        public boolean hasScheduleOf(Leg leg) {
            return leg.departureAt().equals(load.departureAt())
                    && leg.arrivalAt().equals(discharge.arrivalAt());
        }

        /** 区間の寄港の位置の組か（位置を持たない区間は偽）。 */
        public boolean isAtPositionsOf(Leg leg) {
            return leg.hasCallPositions() && leg.loadCallNo() == loadCallNo && leg.dischargeCallNo() == dischargeCallNo;
        }
    }

    public String voyageNumber() {
        return voyageNumber;
    }

    public List<PortCall> portCalls() {
        return List.copyOf(portCalls);
    }

    public String adoptedInfoVersion() {
        return adoptedInfoVersion;
    }

    public UtcInstant acquiredAt() {
        return source.acquiredAt();
    }

    /** 出典（種類・参照・取得時刻）。 */
    public Source source() {
        return source;
    }

    /** 採用値を公開した時刻（取込を経ない仮の航海にはない）。 */
    public Optional<UtcInstant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    /** 集約の版（楽観ロック）。 */
    public long aggregateVersion() {
        return aggregateVersion;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Voyage that
                && voyageNumber.equals(that.voyageNumber)
                && portCalls.equals(that.portCalls)
                && adoptedInfoVersion.equals(that.adoptedInfoVersion)
                && acquiredAt().equals(that.acquiredAt());
    }

    @Override
    public int hashCode() {
        return Objects.hash(voyageNumber, portCalls, adoptedInfoVersion, acquiredAt());
    }

    @Override
    public String toString() {
        return "Voyage[" + voyageNumber + ", " + adoptedInfoVersion + ", 版 " + aggregateVersion + "]";
    }
}
