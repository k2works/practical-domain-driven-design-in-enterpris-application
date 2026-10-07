package com.example.cargotracker.routing.domain.model.aggregates;

import java.util.List;

/**
 * 航海のリポジトリ（送信ポート）。外部原本の取込（US-14、W7）ができるまでは、開発環境の仮の航海データだけを読む。
 */
public interface VoyageRepository {

    /** すべての航海を、寄港の順を保って返す。 */
    List<Voyage> findAll();
}
