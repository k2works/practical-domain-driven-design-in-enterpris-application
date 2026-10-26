package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Duration;

/**
 * 再設定のトークンの用途。用途ごとの期限を持つ（Bolt 38b。確認ポイント 15、SEC-08、IA-INV-12）。繰り返しの依頼の抑えは {@link #RESET} の発行
 * だけで数え、前の未使用のトークンの無効化は用途を問わない。US-19 の荷受人の招待（参照許可）とは別のもの。
 */
@ValueObject
public enum PasswordResetTokenPurpose {
    /** password の再設定（A-05 の依頼）。期限は 30 分。 */
    RESET(Duration.ofMinutes(30)),
    /** 利用者の招待（システム管理者が登録した利用者への初回の設定のリンク。Bolt 38b2c で発行する）。期限は 24 時間。 */
    USER_INVITATION(Duration.ofHours(24));

    private final Duration validity;

    PasswordResetTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    /** 発行から使えなくなるまでの期間。 */
    public Duration validity() {
        return validity;
    }
}
