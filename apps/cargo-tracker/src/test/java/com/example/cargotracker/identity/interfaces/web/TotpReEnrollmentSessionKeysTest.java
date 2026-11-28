package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A-08 の画面だけが使う session の属性の名前（Bolt 33e）。名前は画面の層に残す（ADR-012 の決定 3）が、値を変えると動いている session の
 * 再登録の途中の値が読めなくなるので、Bolt 33b からの文字列で固定する（{@code SessionKeysTest} と同じ形）。
 */
class TotpReEnrollmentSessionKeysTest {

    @Test
    void 再登録の途中の属性の名前はBolt33bからの値のまま() {
        assertThat(TotpReEnrollmentController.PENDING_SECRET)
                .isEqualTo("cargotracker.session.reEnrollmentPendingSecret");
        assertThat(TotpReEnrollmentController.STARTED_AT).isEqualTo("cargotracker.session.reEnrollmentStartedAt");
    }
}
