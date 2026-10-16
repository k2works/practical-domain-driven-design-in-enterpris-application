package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.UUID;

/**
 * 利用者の管理の読み出しの 1 行（Bolt 34）。利用者・所属企業・役割を 1 つの SQL で読み、役割の数だけ行が出る（役割がなければ
 * {@code role} が null の 1 行）。`password_hash` を読まない。
 */
public record UserAdministrationRow(
        UUID id,
        UUID companyId,
        String companyName,
        String companyKind,
        String email,
        String displayName,
        String status,
        long version,
        String role) {}
