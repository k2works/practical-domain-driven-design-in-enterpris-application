package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 企業のリポジトリ（送信ポート）。企業の更新は企業マスター（US-16）で足す。
 */
public interface CompanyRepository {

    void add(Company company);

    Optional<Company> findById(CompanyId id);

    /** ID の集合の企業を、名前・ID の順に返す（Bolt 38b3。登録できる企業の照会）。知らない ID は無視し、空の集合なら空。 */
    List<Company> findAllIn(Set<CompanyId> ids);
}
