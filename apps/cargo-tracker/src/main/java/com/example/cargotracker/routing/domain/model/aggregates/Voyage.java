package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
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
     * 区間が今の航海と一致するか（R-INV-03、【業務の判断】(s)。Bolt 39e2）。航海番号と情報版が同じで、積地の寄港の出発予定が区間の出発予定と同じで、その後ろに
     * 揚地の寄港があり到着予定が区間の到着予定と同じ組が 1 つ以上あるときだけ真になる。同じ情報版で値の違う DE-12 が適用されるので（【業務の判断】(r)）、情報版だけでは
     * 区間の予定の変更を見落とす。2 度寄る航海は組が残っていれば一致する（Bolt 39e2 の決定 (3)）。出発のない寄港は積地に、到着のない寄港は揚地にならない。
     *
     * @return 区間の情報版と予定の組が今の航海にあれば真
     */
    public boolean matchesLeg(Leg leg) {
        if (!voyageNumber.equals(leg.voyageNumber()) || !adoptedInfoVersion.equals(leg.infoVersion())) {
            return false;
        }
        for (int load = 0; load < portCalls.size(); load++) {
            if (isLoadingOf(portCalls.get(load), leg) && hasDischargeOfAfter(leg, load)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoadingOf(PortCall call, Leg leg) {
        return call.port().equals(leg.load()) && leg.departureAt().equals(call.departureAt());
    }

    private boolean hasDischargeOfAfter(Leg leg, int load) {
        return portCalls.subList(load + 1, portCalls.size()).stream()
                .anyMatch(call ->
                        call.port().equals(leg.discharge()) && leg.arrivalAt().equals(call.arrivalAt()));
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
