package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import com.example.cargotracker.shared.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 役割の変更の拒否の文言（Bolt 34f。UI 設計 S-20 の文言の表）。利用状態の {@code UserStatusErrorMessageTest} と同じく、文言と、
 * エラー要約から欄へリンクするか（自分の役割だけリンクしない）をここで確かめる。
 */
class UserRoleErrorMessageTest {

    @ParameterizedTest(name = "{0} は「{1}」")
    @CsvSource({
        "SELF_CHANGE, 自分の役割は変更できません。ほかのシステム管理者に依頼してください。, false",
        "NO_ROLES, 役割を 1 つ以上選んでください。, true",
        "NO_CHANGE, 今の役割から変わっていません。足す役割か外す役割を選んでください。, true",
        "OVERSIGHT_ROLE, システム管理者・監査担当者の役割は、この画面では付けたり外したりできません。, true"
    })
    void 拒否の文言と欄へのリンク(RoleChangeRejectionReason reason, String text, boolean field) {
        assertThat(UserRoleChangeController.errorMessage(RoleChangeRejection.of(reason), CompanyKind.OPERATOR))
                .isEqualTo(new ErrorMessage(text, field));
    }

    @Test
    void 企業の種類に合わない役割は役割の名前と企業の種類の名前で示す() {
        RoleChangeRejection rejection = new RoleChangeRejection(
                RoleChangeRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.ROUTE_DESIGNER, Role.SALES));

        assertThat(UserRoleChangeController.errorMessage(rejection, CompanyKind.SHIPPER))
                .isEqualTo(new ErrorMessage("営業担当者・経路設計者は、荷主企業の利用者には付けられません。", true));
    }

    @Test
    void 競合する役割は業務の役割の名前だけで示す() {
        RoleChangeRejection rejection =
                new RoleChangeRejection(RoleChangeRejectionReason.CONFLICTING_ROLES, Set.of(Role.AUDITOR, Role.SALES));

        assertThat(UserRoleChangeController.errorMessage(rejection, CompanyKind.OPERATOR))
                .isEqualTo(new ErrorMessage("システム管理者・監査担当者と、業務データを変更できる役割（営業担当者）は併せて付けられません。", true));
    }

    @Test
    void 自分の役割の拒否は詳細の案内と同じ文言() {
        assertThat(UserRoleChangeController.errorMessage(
                                RoleChangeRejection.of(RoleChangeRejectionReason.SELF_CHANGE), CompanyKind.OPERATOR)
                        .text())
                .isEqualTo(UserDetailPage.SELF_ROLE_NOTE);
    }
}
