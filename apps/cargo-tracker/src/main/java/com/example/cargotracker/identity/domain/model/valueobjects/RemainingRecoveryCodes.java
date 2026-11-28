package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 残りの回復コード。まだ使っていない回復コードの数（Bolt 38d2。SEC-10 の残りの回復コードの案内）。回復コードでログインした後の
 * A-08 と印の知らせが、{@value #LOW_THRESHOLD} 個以下（少ない）のときだけ数を示し、0 個（ない）ならログインに戻る道がないことを示す。
 * 回復コードを作り直す道は A-08 の再登録だけ（SEC-10 (2)）。
 *
 * @param count まだ使っていない回復コードの数（0 以上）
 */
@ValueObject
public record RemainingRecoveryCodes(int count) {

    /** 少ないとするしきい値（この数以下で案内する。W6 の確認ポイント 14）。 */
    public static final int LOW_THRESHOLD = 3;

    public RemainingRecoveryCodes {
        if (count < 0) {
            throw new IllegalArgumentException("残りの回復コードの数は 0 以上: " + count);
        }
    }

    /** 残りが少ない（{@value #LOW_THRESHOLD} 個以下）か。0 個も少ないに入る。 */
    public boolean isLow() {
        return count <= LOW_THRESHOLD;
    }

    /** 残っていないか。 */
    public boolean isEmpty() {
        return count == 0;
    }
}
