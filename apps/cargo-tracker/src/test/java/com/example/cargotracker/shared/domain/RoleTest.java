package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void 役割はBR15の8つに固定する() {
        assertThat(Role.values())
                .extracting(Role::displayName)
                .containsExactly("荷主担当者", "営業担当者", "経路設計者", "追跡管理者", "データ責任者", "カスタマーサポート", "システム管理者", "監査担当者");
    }
}
