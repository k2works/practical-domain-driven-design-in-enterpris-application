package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.UUID;

/**
 * 利用者・所属企業・役割を結合した 1 行（Bolt 32c）。役割の数だけ行が出る。役割がなければ {@code role} が null の 1 行。
 */
public record UserStandingRow(
        UUID id,
        UUID companyId,
        String email,
        String displayName,
        String passwordHash,
        String status,
        String companyName,
        String companyKind,
        boolean companyActive,
        String role) {}
