package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 利用状態の変更の拒否の文言（Bolt 34d。UI 設計 S-20 の文言の表）。セキュリティの統合テストは文言を比べないので（T-145）、文言と、
 * エラー要約から欄へリンクするかをここで確かめる。
 */
class UserStatusErrorMessageTest {

    @ParameterizedTest(name = "{0} は「{2}」")
    @CsvSource({
        "SELF_CHANGE, ACTIVE, 自分の利用状態は変更できません。ほかのシステム管理者に依頼してください。, false",
        "OVERSIGHT_ROLE, ACTIVE, システム管理者・監査担当者の利用停止と再開は、この画面では行いません。, false",
        "NO_CHANGE, SUSPENDED, 利用状態は既に利用停止です。最新の内容を確かめてください。, false",
        "NO_CHANGE, ACTIVE, 利用状態は既に利用中です。最新の内容を確かめてください。, false",
        "REASON_REQUIRED, ACTIVE, 利用終了・異動・緊急停止のどれかを選んでください。, true"
    })
    void 拒否の文言と欄へのリンク(StatusChangeRejection rejection, UserStatus current, String text, boolean field) {
        assertThat(UserStatusChangeController.errorMessage(rejection, current))
                .isEqualTo(new ErrorMessage(text, field));
    }

    @Test
    void 自分と監督の役割の拒否は詳細の案内と同じ文言() {
        assertThat(UserStatusChangeController.errorMessage(StatusChangeRejection.SELF_CHANGE, UserStatus.ACTIVE)
                        .text())
                .isEqualTo(UserDetailPage.SELF_STATUS_NOTE);
        assertThat(UserStatusChangeController.errorMessage(StatusChangeRejection.OVERSIGHT_ROLE, UserStatus.ACTIVE)
                        .text())
                .isEqualTo(UserDetailPage.OVERSIGHT_STATUS_NOTE);
    }
}
