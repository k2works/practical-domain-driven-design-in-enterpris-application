package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;

/**
 * 画面の単体テストで使う、認証された利用者の値（Bolt 14）。認証の前の仮の主体の値と違えて、
 * コントローラーが認証の主体から操作者と企業を受け取ることを確かめる。
 */
public final class TestActors {

    public static final CompanyId SHIPPER_COMPANY =
            new CompanyId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    public static final UserId SHIPPER_USER = new UserId(UUID.fromString("10000000-0000-0000-0000-000000000101"));
    public static final CompanyId STAFF_COMPANY =
            new CompanyId(UUID.fromString("10000000-0000-0000-0000-000000000003"));
    public static final UserId STAFF_USER = new UserId(UUID.fromString("10000000-0000-0000-0000-000000000301"));

    private TestActors() {}
}
