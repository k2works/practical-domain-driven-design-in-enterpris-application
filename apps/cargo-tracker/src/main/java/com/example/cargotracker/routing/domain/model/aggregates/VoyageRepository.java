package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 航海のリポジトリ（送信ポート）。取り込んで採用した航海の値（DE-12）を書き、候補の算出と確定と評価が読む（Bolt 39e）。
 * 読みは航海と寄港を 1 文で読み、寄港は寄港の順を保つ。
 */
public interface VoyageRepository {

    /** 航海番号の航海。なければ空。 */
    Optional<Voyage> findByVoyageNumber(String voyageNumber);

    /** 航海番号の航海だけを返す。ない番号は含まない。 */
    List<Voyage> findByVoyageNumbers(Collection<String> voyageNumbers);

    /**
     * 候補の算出に使う航海。判定時刻より後に出発する寄港を持つ航海だけを返す（上の端は置かない。期限に間に合わない候補とその理由を画面から
     * 消さない。R-INV-02）。寄港は判定時刻より前の行も落とさない。
     */
    List<Voyage> findCandidateVoyages(UtcInstant departingAfter);

    /**
     * 新しい航海を保存する。
     *
     * @throws DuplicateVoyageException 同じ航海番号の航海がある（呼び手のトランザクションは続けられる）
     */
    void save(Voyage voyage);

    /**
     * 航海の値と寄港を置き換える。
     *
     * @throws ConcurrentVoyageUpdateException 読み込んだ後にほかの更新が先に保存された（行は変えない）
     */
    void update(Voyage voyage);
}
