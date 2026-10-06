package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Optional;

/**
 * 企業のリポジトリ（送信ポート）。企業の更新は企業マスター（US-16）で足す。
 */
public interface CompanyRepository {

    void add(Company company);

    Optional<Company> findById(CompanyId id);
}
