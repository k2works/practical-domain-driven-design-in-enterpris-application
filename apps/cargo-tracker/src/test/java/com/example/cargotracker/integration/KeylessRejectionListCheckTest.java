package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * 行 21 の競合の判定（Bolt 40。PERF-06 の run 43318310748 の失敗）。一覧を照会して空だったとき、却下のトランザクションの間に消えたのか、
 * 照会の前に却下がコミットを終えたのかを、却下の完了を待って見分ける。
 */
class KeylessRejectionListCheckTest {

    @Test
    void 一覧が空でないときは却下の完了を待たずに通る() {
        KeylessRejectionListCheck.assertListedDuringRejection(false, () -> {
            throw new AssertionError("空でないのに完了を待った");
        });
    }

    @Test
    void 一覧が空でも却下が完了していれば通る() {
        KeylessRejectionListCheck.assertListedDuringRejection(true, () -> "完了");
    }

    @Test
    void 一覧が空で却下が完了しないときは失敗する() {
        assertThatThrownBy(() -> KeylessRejectionListCheck.assertListedDuringRejection(true, () -> {
                    throw new TimeoutException();
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("却下");
    }

    @Test
    void 一覧が空で却下が失敗したときも失敗する() {
        assertThatThrownBy(() -> KeylessRejectionListCheck.assertListedDuringRejection(true, () -> {
                    throw new IllegalStateException("却下が失敗した");
                }))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void 待つ上限は5秒() {
        assertThat(KeylessRejectionListCheck.COMPLETION_WAIT_SECONDS).isEqualTo(5);
    }
}
