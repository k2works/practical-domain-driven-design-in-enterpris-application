package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 利用者の管理の詳細の読み出しの 1 行（Bolt 38c で一覧の行 {@link UserAdministrationRow} から分けた）。一覧の項目に、ログインの失敗の
 * ロックの期限と TOTP の資格の登録の時刻を足したもの（どちらも記録がなければ null。2 つの表は主キーが利用者 ID なので行は増えない）。
 * 役割の数だけ行が出る。`password_hash` と TOTP の秘密を読まない。
 */
public record UserDetailRow(
        UUID id,
        UUID companyId,
        String companyName,
        String companyKind,
        String email,
        String displayName,
        String status,
        long version,
        String role,
        OffsetDateTime lockedUntil,
        OffsetDateTime totpEnrolledAt) {}
