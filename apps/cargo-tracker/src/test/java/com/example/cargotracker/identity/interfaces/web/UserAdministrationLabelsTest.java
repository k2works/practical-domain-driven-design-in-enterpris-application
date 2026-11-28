package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** S-20 の画面の名前（Bolt 34f。UI 設計 S-20）。一覧・詳細・確認の画面はこの名前を使う。 */
class UserAdministrationLabelsTest {

    @ParameterizedTest(name = "{0} は「{1}」")
    @CsvSource({"ACTIVE, 利用中", "SUSPENDED, 利用停止"})
    void 利用状態の名前(UserStatus status, String name) {
        assertThat(UserAdministrationLabels.status(status)).isEqualTo(name);
    }

    @ParameterizedTest(name = "{0} は「{1}」")
    @CsvSource({"USAGE_ENDED, 利用終了", "TRANSFERRED, 異動", "EMERGENCY, 緊急停止"})
    void 停止の理由の名前(SuspensionReason reason, String name) {
        assertThat(UserAdministrationLabels.suspensionReason(reason)).isEqualTo(name);
    }

    @ParameterizedTest(name = "{0} は「{1}」")
    @CsvSource({"SHIPPER, 荷主企業", "CONSIGNEE, 荷受人の企業", "OPERATOR, 運用する会社"})
    void 企業の種類の名前(CompanyKind kind, String name) {
        assertThat(UserAdministrationLabels.companyKind(kind)).isEqualTo(name);
    }

    @Test
    void 役割の名前は宣言の順に中黒でつなぐ() {
        assertThat(UserAdministrationLabels.roles(Set.of(Role.AUDITOR, Role.ROUTE_DESIGNER, Role.SALES)))
                .isEqualTo("営業担当者・経路設計者・監査担当者");
    }

    @Test
    void 役割がなければなし() {
        assertThat(UserAdministrationLabels.roles(Set.of())).isEqualTo("なし");
    }
}
