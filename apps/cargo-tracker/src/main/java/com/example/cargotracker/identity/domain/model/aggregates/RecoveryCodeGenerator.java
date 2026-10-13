package com.example.cargotracker.identity.domain.model.aggregates;

import java.util.List;

/**
 * 回復コードの生成のポート（Bolt 31b、SEC-09）。一組の回復コードの平文を乱数で作る。実装は {@code identity.infrastructure}。
 */
@FunctionalInterface
public interface RecoveryCodeGenerator {

    /** {@value RecoveryCode#CODES_PER_USER} 個の、重複のない回復コードの平文。 */
    List<String> generate();
}
