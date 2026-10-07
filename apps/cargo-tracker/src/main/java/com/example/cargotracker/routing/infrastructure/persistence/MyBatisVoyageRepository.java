package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 航海のリポジトリの MyBatis 実装（Bolt 17）。航海と寄港の表を組み立て、寄港は寄港の順に並べる。
 * 寄港が 2 つに満たない航海（候補にならない）は読み飛ばさず、航海の不変条件の違反として失敗させる（データの誤りに気づくため）。
 */
@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final RoutingReferenceMapper mapper;

    public MyBatisVoyageRepository(RoutingReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Voyage> findAll() {
        Map<String, List<PortCall>> calls = mapper.selectPortCalls().stream()
                .collect(Collectors.groupingBy(
                        PortCallRow::voyageNumber,
                        Collectors.mapping(
                                row -> new PortCall(
                                        new Location(row.portUnlocode()),
                                        toUtc(row.arrivalAt()),
                                        toUtc(row.departureAt())),
                                Collectors.toList())));
        return mapper.selectVoyages().stream()
                .map(row -> new Voyage(
                        row.voyageNumber(),
                        calls.getOrDefault(row.voyageNumber(), List.of()),
                        row.adoptedInfoVersion(),
                        toUtc(row.acquiredAt())))
                .toList();
    }

    private static UtcInstant toUtc(OffsetDateTime dateTime) {
        return dateTime == null ? null : new UtcInstant(dateTime.toInstant());
    }
}
