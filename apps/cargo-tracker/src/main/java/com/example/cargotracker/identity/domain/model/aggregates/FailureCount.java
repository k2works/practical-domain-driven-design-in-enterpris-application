package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 失敗の数えの結果。その失敗を数えたか（ロック中は数えない）と、数えた後のログインの失敗（Bolt 32、IA-INV-03）。数えたかは
 * リポジトリの条件付きの更新の件数で決まるので、同時の失敗でも「この失敗でロックした」を 1 つだけに見分けられる。
 *
 * @param counted その失敗を数えたか（ロック中・記録のない利用者では false）
 * @param after 数えた後のログインの失敗
 */
public record FailureCount(boolean counted, LoginFailure after) {

    public FailureCount {
        Objects.requireNonNull(after, "after");
    }

    /** この失敗でロックしたか（数えて、数えた後がその時刻にロック中）。 */
    public boolean lockedBy(UtcInstant at) {
        return counted && after.isLocked(at);
    }
}
