package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * メモリ上の航海のリポジトリ。シナリオが航海を置く。
 */
public class InMemoryVoyageRepository implements VoyageRepository {

    private final List<Voyage> voyages = new ArrayList<>();

    @Override
    public synchronized List<Voyage> findAll() {
        return List.copyOf(voyages);
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

    public synchronized void clear() {
        voyages.clear();
    }
}
