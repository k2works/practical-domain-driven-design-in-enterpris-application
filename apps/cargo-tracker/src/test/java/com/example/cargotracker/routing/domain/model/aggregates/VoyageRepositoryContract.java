package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航海のリポジトリの契約（Bolt 39e の行 14〜18・29・30・32・33）。メモリと H2 と PostgreSQL の実装が継いで同じ約束を確かめる。
 * 共有の DB にはほかの航海があるので、航海番号はテストのインスタンスごとの印で作り、結果はその印で絞る。
 * {@code @Transactional} は継ぐ側ではなくこのクラスに付ける（T-231）。書いた行はロールバックで消える。
 */
@Transactional
public abstract class VoyageRepositoryContract {

    private static final Location TOKYO = new Location("JPTYO");
    private static final Location HONG_KONG = new Location("HKHKG");
    private static final Location SINGAPORE = new Location("SGSIN");
    private static final Location COLOMBO = new Location("LKCMB");
    private static final Location ROTTERDAM = new Location("NLRTM");
    /** 判定時刻（マイクロ秒までの DB の精度）。 */
    private static final Instant JUDGED_AT = Instant.parse("2097-10-07T03:00:00.123456Z");

    private static final int MAX_VOYAGES_ON_S06 = 60;
    private static final Logger LOG = LoggerFactory.getLogger(VoyageRepositoryContract.class);

    protected abstract VoyageRepository voyages();

    private final String mark = UUID.randomUUID().toString().substring(0, 8);

    private String number(String name) {
        return "C-" + mark + "-" + name;
    }

    private static UtcInstant at(Instant instant) {
        return new UtcInstant(instant);
    }

    private static UtcInstant hoursAfterJudged(int hours) {
        return at(JUDGED_AT.plusSeconds(hours * 3600L));
    }

    private static List<PortCall> direct(UtcInstant departure, UtcInstant arrival) {
        return List.of(new PortCall(TOKYO, null, departure), new PortCall(ROTTERDAM, arrival, null));
    }

    private static List<PortCall> threeCalls() {
        return List.of(
                new PortCall(TOKYO, null, hoursAfterJudged(1)),
                new PortCall(SINGAPORE, hoursAfterJudged(100), hoursAfterJudged(110)),
                new PortCall(ROTTERDAM, hoursAfterJudged(200), null));
    }

    /** 上限いっぱい（寄港 60）。同じ 2 つの港を順に回る。 */
    private static List<PortCall> sixtyCalls() {
        List<PortCall> calls = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            calls.add(new PortCall(
                    i % 2 == 0 ? TOKYO : ROTTERDAM, hoursAfterJudged(10 * i + 1), hoursAfterJudged(10 * i + 2)));
        }
        return calls;
    }

    private static VoyageAdoption adoption(
            String voyageNumber, List<PortCall> calls, String infoVersion, UtcInstant published) {
        return new VoyageAdoption(
                voyageNumber,
                calls,
                infoVersion,
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), at(JUDGED_AT)),
                published);
    }

    private Voyage saved(String name, List<PortCall> calls) {
        Voyage voyage = Voyage.adoptedFrom(adoption(number(name), calls, "1", hoursAfterJudged(0)));
        voyages().save(voyage);
        return voyage;
    }

    private Voyage read(String name) {
        return voyages().findByVoyageNumber(number(name)).orElseThrow();
    }

    /** このテストの航海番号だけに絞った窓の一覧。 */
    private List<Voyage> candidateVoyages(UtcInstant departingAfter) {
        return voyages().findCandidateVoyages(departingAfter).stream()
                .filter(voyage -> voyage.voyageNumber().startsWith("C-" + mark))
                .toList();
    }

    private static Set<String> numbersOf(List<Voyage> voyages) {
        return voyages.stream().map(Voyage::voyageNumber).collect(Collectors.toSet());
    }

    @Test
    void Bolt39e_行14_saveした航海をfindByVoyageNumberで寄港の順と出典と公開した時刻と版0のまま読める() {
        List<PortCall> calls = threeCalls();
        Source source = new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), at(JUDGED_AT));
        Voyage voyage =
                Voyage.adoptedFrom(new VoyageAdoption(number("A"), calls, "2026-11-001", source, hoursAfterJudged(5)));

        voyages().save(voyage);

        Voyage found = read("A");
        assertThat(found).isEqualTo(voyage);
        assertThat(found.portCalls()).containsExactlyElementsOf(calls);
        assertThat(found.adoptedInfoVersion()).isEqualTo("2026-11-001");
        assertThat(found.source()).isEqualTo(source);
        assertThat(found.publishedAt()).contains(hoursAfterJudged(5));
        assertThat(found.aggregateVersion()).isZero();
    }

    @Test
    void Bolt39e_行4_保存した航海を読み直して同じ採用値を適用しても適用されない再配信で版が進まない() {
        VoyageAdoption same = adoption(number("A"), threeCalls(), "1", hoursAfterJudged(5));
        voyages().save(Voyage.adoptedFrom(same));

        boolean applied = read("A").apply(same);

        assertThat(applied).isFalse();
    }

    static Stream<Arguments> updatedPortCalls() {
        return Stream.of(
                Arguments.of("寄港 3 → 2", direct(hoursAfterJudged(1), hoursAfterJudged(90))),
                Arguments.of("寄港 3 → 60（上限いっぱい）", sixtyCalls()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updatedPortCalls")
    void Bolt39e_行15_更新で寄港が置き換わり版が1増えほかの航海の行は変わらない(String description, List<PortCall> newCalls) {
        saved("A", threeCalls());
        Voyage other = saved("B", threeCalls());
        Voyage voyage = read("A");
        Source newSource = new Source(SourceKind.FIELD_RECORD, UUID.randomUUID().toString(), hoursAfterJudged(3));
        voyage.apply(new VoyageAdoption(number("A"), newCalls, "2", newSource, hoursAfterJudged(9)));

        voyages().update(voyage);

        Voyage found = read("A");
        assertThat(found.portCalls()).as(description).containsExactlyElementsOf(newCalls);
        assertThat(found.adoptedInfoVersion()).isEqualTo("2");
        assertThat(found.source()).isEqualTo(newSource);
        assertThat(found.publishedAt()).contains(hoursAfterJudged(9));
        assertThat(found.aggregateVersion()).isEqualTo(1);
        assertThat(read("B")).isEqualTo(other);
        assertThat(read("B").portCalls()).containsExactlyElementsOf(threeCalls());
        assertThat(read("B").aggregateVersion()).isZero();
    }

    @Test
    void Bolt39e_行16_版の違う更新は競合の例外で航海の行も寄港の行も変わらない() {
        saved("A", threeCalls());
        Voyage stale = read("A");
        Voyage fresh = read("A");
        fresh.apply(adoption(number("A"), direct(hoursAfterJudged(1), hoursAfterJudged(90)), "2", hoursAfterJudged(9)));
        voyages().update(fresh);
        stale.apply(
                adoption(number("A"), direct(hoursAfterJudged(2), hoursAfterJudged(91)), "3", hoursAfterJudged(10)));

        VoyageRepository repository = voyages();
        assertThatThrownBy(() -> repository.update(stale)).isInstanceOf(ConcurrentVoyageUpdateException.class);

        Voyage found = read("A");
        assertThat(found).isEqualTo(fresh);
        assertThat(found.adoptedInfoVersion()).isEqualTo("2");
        assertThat(found.portCalls()).containsExactlyElementsOf(fresh.portCalls());
        assertThat(found.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void Bolt39e_行17_同じ航海番号のsaveは重複の例外で同じトランザクションで続けて読める() {
        Voyage first = saved("A", threeCalls());
        Voyage same = Voyage.adoptedFrom(
                adoption(number("A"), direct(hoursAfterJudged(1), hoursAfterJudged(90)), "2", hoursAfterJudged(9)));

        VoyageRepository repository = voyages();
        assertThatThrownBy(() -> repository.save(same)).isInstanceOf(DuplicateVoyageException.class);

        assertThat(read("A")).isEqualTo(first);
    }

    @Test
    void Bolt39e_行18_ない航海番号は空() {
        assertThat(voyages().findByVoyageNumber(number("MISSING"))).isEmpty();
    }

    static Stream<Arguments> timeWindow() {
        return Stream.of(
                Arguments.of("判定時刻の 1 秒後に出発", 1L, true),
                Arguments.of("判定時刻と同時刻に出発（含めない）", 0L, false),
                Arguments.of("判定時刻の 1 秒前に出発（過去の航海）", -1L, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timeWindow")
    void Bolt39e_行29_判定時刻より後に出発する寄港のある航海だけを返し同時刻と過去は返さない(
            String description, long secondsAfterJudged, boolean expected) {
        UtcInstant departure = at(JUDGED_AT.plusSeconds(secondsAfterJudged));
        saved("A", direct(departure, hoursAfterJudged(90)));

        assertThat(candidateVoyages(at(JUDGED_AT))).as(description).hasSize(expected ? 1 : 0);
    }

    @Test
    void Bolt39e_行30_窓の上の端はなく希望到着期限より後に出発する航海も返す() {
        saved("FAR", direct(at(Instant.parse("2199-01-01T00:00:00Z")), at(Instant.parse("2199-02-01T00:00:00Z"))));

        assertThat(numbersOf(candidateVoyages(at(JUDGED_AT)))).containsExactly(number("FAR"));
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("直行", List.of("DIRECT")),
                Arguments.of("積替え 1 回", List.of("FEEDER", "MAIN")),
                Arguments.of("積替え 2 回", List.of("FEEDER2", "MIDDLE", "LAST")));
    }

    /** 区間ごとに、最初の寄港が判定時刻より前の航海（途中の寄港から乗る）を置く。 */
    private void savedRoute(String name) {
        switch (name) {
            case "DIRECT" -> saved(name, midJoin(HONG_KONG, TOKYO, ROTTERDAM, 10, 400));
            case "FEEDER" -> saved(name, midJoin(HONG_KONG, TOKYO, SINGAPORE, 10, 200));
            case "MAIN" -> saved(name, midJoin(COLOMBO, SINGAPORE, ROTTERDAM, 210, 500));
            case "FEEDER2" -> saved(name, midJoin(HONG_KONG, TOKYO, SINGAPORE, 10, 200));
            case "MIDDLE" -> saved(name, midJoin(HONG_KONG, SINGAPORE, COLOMBO, 210, 300));
            case "LAST" -> saved(name, midJoin(HONG_KONG, COLOMBO, ROTTERDAM, 310, 500));
            default -> throw new IllegalArgumentException(name);
        }
    }

    /** 判定時刻より前に出発した寄港 → 判定時刻より後に出発する積地 → 揚地の航海。 */
    private static List<PortCall> midJoin(
            Location before, Location load, Location discharge, int departAfter, int arriveAfter) {
        return List.of(
                new PortCall(before, null, hoursAfterJudged(-48)),
                new PortCall(load, hoursAfterJudged(departAfter - 5), hoursAfterJudged(departAfter)),
                new PortCall(discharge, hoursAfterJudged(arriveAfter), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void Bolt39e_行32_区間の航海が窓の一覧に入り途中の寄港から乗る航海の判定時刻より前の寄港も落とさない(String description, List<String> names) {
        names.forEach(this::savedRoute);
        RouteSpecification specification =
                new RouteSpecification(TOKYO, ROTTERDAM, at(Instant.parse("2199-01-01T00:00:00Z")), "GENERAL");

        List<Voyage> found = candidateVoyages(at(JUDGED_AT));

        List<String> expected = names.stream().map(this::number).toList();
        assertThat(numbersOf(found)).as(description).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(found)
                .allSatisfy(voyage ->
                        assertThat(voyage.portCalls()).as("判定時刻より前の寄港の行も残る").hasSize(3));
        assertThat(new RouteCandidateFinder().find(specification, found, at(JUDGED_AT)))
                .as(description)
                .extracting(legs -> legs.stream().map(Leg::voyageNumber).toList())
                .contains(expected);
    }

    @Test
    void Bolt39e_行29_寄港は寄港の順に窓の一覧で返る() {
        saved("A", threeCalls());

        assertThat(candidateVoyages(at(JUDGED_AT)).getFirst().portCalls()).containsExactlyElementsOf(threeCalls());
    }

    static Stream<Arguments> numberLookups() {
        return Stream.of(
                Arguments.of("1 つ", List.of("A"), Set.of("A")),
                Arguments.of("2 つ", List.of("A", "B"), Set.of("A", "B")),
                Arguments.of("ない番号は含まない", List.of("A", "MISSING"), Set.of("A")),
                Arguments.of("ない番号だけ", List.of("MISSING"), Set.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numberLookups")
    void Bolt39e_行33_航海番号の区間の航海だけを返しない番号は含まない(String description, List<String> requested, Set<String> expected) {
        saved("A", threeCalls());
        saved("B", threeCalls());
        saved("C", threeCalls());

        List<Voyage> found = voyages()
                .findByVoyageNumbers(requested.stream().map(this::number).toList());

        assertThat(numbersOf(found))
                .as(description)
                .isEqualTo(expected.stream().map(this::number).collect(Collectors.toSet()));
    }

    @Test
    void Bolt39e_行33_空の一覧は空を返しSQLの空のINを出さない() {
        saved("A", threeCalls());

        assertThat(voyages().findByVoyageNumbers(List.of())).isEmpty();
    }

    /** S-06 を開くときの上限（20 候補 × 3 区間 = 60 航海）。読みの時間は参考の値で、時間では落とさない（決定 (4)。1 秒を超えたら値を記録して止める）。 */
    @Test
    void Bolt39f2_行17_上限の60航海の寄港60をすべて寄港の順に読み読みの時間をログに出す() {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < MAX_VOYAGES_ON_S06; i++) {
            saved("U" + i, sixtyCalls());
            numbers.add(number("U" + i));
        }

        long started = System.nanoTime();
        List<Voyage> found = voyages().findByVoyageNumbers(numbers);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        LOG.info(
                "PERF-01 S-06 上限の読み（{} 航海 × 寄港 60）: {} ms（{}）",
                numbers.size(),
                elapsedMillis,
                voyages().getClass().getSimpleName());
        assertThat(numbersOf(found)).containsExactlyInAnyOrderElementsOf(numbers);
        assertThat(found).allSatisfy(voyage -> assertThat(voyage.portCalls()).containsExactlyElementsOf(sixtyCalls()));
    }
}
