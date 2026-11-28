package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.UUID;

/**
 * 利用者・所属企業・役割・担当企業を結合した 1 行（Bolt 32c・38f）。役割 × 担当企業の数だけ行が出る。役割がなければ {@code role} が
 * null、担当企業がなければ {@code assignedCompanyId} が null。
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
        String role,
        UUID assignedCompanyId) {}
