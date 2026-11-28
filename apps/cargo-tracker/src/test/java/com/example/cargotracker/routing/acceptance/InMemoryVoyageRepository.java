package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentVoyageUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateVoyageException;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * メモリ上の航海のリポジトリ。シナリオが航海を置く。読むときは写しを返す（航海は変わるので、保存せずに中身を変えない）。
 */
public class InMemoryVoyageRepository implements VoyageRepository {

    private final List<Voyage> voyages = new ArrayList<>();

    /** すべての航海（テストの確かめに使う。本番のポートにはない）。 */
    public synchronized List<Voyage> findAll() {
        return voyages.stream().map(InMemoryVoyageRepository::copy).toList();
    }

    @Override
    public synchronized Optional<Voyage> findByVoyageNumber(String voyageNumber) {
        return voyages.stream()
                .filter(voyage -> voyage.voyageNumber().equals(voyageNumber))
                .findFirst()
                .map(InMemoryVoyageRepository::copy);
    }

    @Override
    public synchronized List<Voyage> findByVoyageNumbers(Collection<String> voyageNumbers) {
        return voyages.stream()
                .filter(voyage -> voyageNumbers.contains(voyage.voyageNumber()))
                .map(InMemoryVoyageRepository::copy)
                .toList();
    }

    @Override
    public synchronized List<Voyage> findCandidateVoyages(UtcInstant departingAfter) {
        return voyages.stream()
                .filter(voyage -> voyage.portCalls().stream()
                        .map(PortCall::departureAt)
                        .anyMatch(departure ->
                                departure != null && departure.instant().isAfter(departingAfter.instant())))
                .map(InMemoryVoyageRepository::copy)
                .toList();
    }

    @Override
    public synchronized void save(Voyage voyage) {
        if (voyages.stream().anyMatch(each -> each.voyageNumber().equals(voyage.voyageNumber()))) {
            throw new DuplicateVoyageException(voyage.voyageNumber());
        }
        voyages.add(copy(voyage));
    }

    @Override
    public synchronized void update(Voyage voyage) {
        Voyage stored = voyages.stream()
                .filter(each -> each.voyageNumber().equals(voyage.voyageNumber()))
                .findFirst()
                .orElseThrow(
                        () -> new ConcurrentVoyageUpdateException(voyage.voyageNumber(), voyage.aggregateVersion()));
        if (stored.aggregateVersion() != voyage.aggregateVersion()) {
            throw new ConcurrentVoyageUpdateException(voyage.voyageNumber(), voyage.aggregateVersion());
        }
        voyages.remove(stored);
        voyages.add(reconstituted(voyage, voyage.aggregateVersion() + 1));
    }

    /** 航海を足す。航海番号はデータベースの主キーと同じく重ならない（同じ番号は {@link #replace} を使う。Bolt 37c）。 */
    public synchronized void add(Voyage voyage) {
        if (voyages.stream().anyMatch(each -> each.voyageNumber().equals(voyage.voyageNumber()))) {
            throw new IllegalArgumentException("同じ航海番号の航海があります: " + voyage.voyageNumber());
        }
        voyages.add(voyage);
    }

    /** 同じ航海番号の航海を置き換える（外部原本の取込で情報版や取得時刻が変わったことを表す。Bolt 37c）。 */
    public synchronized void replace(Voyage voyage) {
        if (!voyages.removeIf(each -> each.voyageNumber().equals(voyage.voyageNumber()))) {
            throw new IllegalArgumentException("置き換える航海がありません: " + voyage.voyageNumber());
        }
        voyages.add(voyage);
    }

    /** 航海をなくす（外部原本の取込で欠航になったことを表す。Bolt 37d2）。 */
    public synchronized void remove(String voyageNumber) {
        if (!voyages.removeIf(each -> each.voyageNumber().equals(voyageNumber))) {
            throw new IllegalArgumentException("なくす航海がありません: " + voyageNumber);
        }
    }

    public synchronized void clear() {
        voyages.clear();
    }

    private static Voyage copy(Voyage voyage) {
        return reconstituted(voyage, voyage.aggregateVersion());
    }

    private static Voyage reconstituted(Voyage voyage, long aggregateVersion) {
        return Voyage.reconstitute(
                voyage.voyageNumber(),
                voyage.portCalls(),
                voyage.adoptedInfoVersion(),
                voyage.source(),
                voyage.publishedAt().orElse(null),
                aggregateVersion);
    }
}
