package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentVoyageUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateVoyageException;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航海のリポジトリの MyBatis 実装（Bolt 17。書き込みは Bolt 39e）。航海と寄港は JOIN の 1 文で読み、寄港は寄港の順に並べる
 * （文の開始の時点の 1 つのスナップショットで組を読む。別の文で読むと、間にコミットされた初めての航海の寄港が空になる。決定 (10)）。
 * 寄港が 2 つに満たない航海（候補にならない）は読み飛ばさず、航海の不変条件の違反として失敗させる（データの誤りに気づくため）。
 * 更新は版の条件付き UPDATE を先にし、0 件なら例外にして寄港を触らない。そのあと寄港は消して入れ直す。
 */
@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final RoutingReferenceMapper mapper;
    private final Clock clock;

    public MyBatisVoyageRepository(RoutingReferenceMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(String voyageNumber) {
        return findByVoyageNumbers(List.of(voyageNumber)).stream().findFirst();
    }

    @Override
    public List<Voyage> findByVoyageNumbers(Collection<String> voyageNumbers) {
        if (voyageNumbers.isEmpty()) {
            // IN () は書けない（Bolt 38g の前例）
            return List.of();
        }
        return toVoyages(mapper.selectVoyagesByNumbers(voyageNumbers));
    }

    @Override
    public List<Voyage> findCandidateVoyages(UtcInstant departingAfter) {
        return toVoyages(mapper.selectCandidateVoyages(toOffset(departingAfter)));
    }

    /**
     * 新しい航海を保存する。同じ航海番号の主キーに違反したら、セーブポイントに戻してドメインの例外にする
     * （PostgreSQL は制約違反でトランザクションを中断するため。経路設計案件の保存と同じ）。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void save(Voyage voyage) {
        try {
            mapper.insertVoyage(toRow(voyage));
        } catch (DuplicateKeyException _) {
            throw new DuplicateVoyageException(voyage.voyageNumber());
        }
        insertPortCalls(voyage);
    }

    @Override
    public void update(Voyage voyage) {
        if (mapper.updateVoyage(toRow(voyage)) == 0) {
            throw new ConcurrentVoyageUpdateException(voyage.voyageNumber(), voyage.aggregateVersion());
        }
        mapper.deletePortCalls(voyage.voyageNumber());
        insertPortCalls(voyage);
    }

    private void insertPortCalls(Voyage voyage) {
        List<PortCall> calls = voyage.portCalls();
        for (int i = 0; i < calls.size(); i++) {
            PortCall call = calls.get(i);
            mapper.insertPortCall(new PortCallRow(
                    voyage.voyageNumber(),
                    i + 1,
                    call.port().unLocode(),
                    toOffset(call.arrivalAt()),
                    toOffset(call.departureAt())));
        }
    }

    private VoyageRow toRow(Voyage voyage) {
        return new VoyageRow(
                voyage.voyageNumber(),
                voyage.adoptedInfoVersion(),
                voyage.source().kind().name(),
                voyage.source().reference(),
                toOffset(voyage.acquiredAt()),
                voyage.publishedAt().map(MyBatisVoyageRepository::toOffset).orElse(null),
                voyage.aggregateVersion(),
                OffsetDateTime.now(clock));
    }

    /** 行は航海番号と寄港の順に並んでいる。 */
    private static List<Voyage> toVoyages(List<VoyagePortCallRow> rows) {
        return rows.stream()
                .collect(
                        Collectors.groupingBy(VoyagePortCallRow::voyageNumber, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(MyBatisVoyageRepository::toVoyage)
                .toList();
    }

    private static Voyage toVoyage(List<VoyagePortCallRow> rows) {
        VoyagePortCallRow first = rows.getFirst();
        return Voyage.reconstitute(
                first.voyageNumber(),
                rows.stream()
                        .map(row -> new PortCall(
                                new Location(row.portUnlocode()), toUtc(row.arrivalAt()), toUtc(row.departureAt())))
                        .toList(),
                first.adoptedInfoVersion(),
                new Source(SourceKind.valueOf(first.sourceKind()), first.sourceRef(), toUtc(first.acquiredAt())),
                toUtc(first.publishedAt()),
                first.version());
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant == null ? null : instant.instant().atOffset(ZoneOffset.UTC);
    }

    private static UtcInstant toUtc(OffsetDateTime dateTime) {
        return dateTime == null ? null : new UtcInstant(dateTime.toInstant());
    }
}
