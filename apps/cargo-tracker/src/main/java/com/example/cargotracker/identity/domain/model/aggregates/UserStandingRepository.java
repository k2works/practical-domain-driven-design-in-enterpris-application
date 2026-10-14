package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;

/**
 * 利用者の立場のリポジトリ（送信ポート。Bolt 32c、ADR-012 の見直しの 3）。利用者・役割・所属企業を 1 回の読み出しで返す。
 */
public interface UserStandingRepository {

    Optional<UserStanding> findById(UserId id);
}
