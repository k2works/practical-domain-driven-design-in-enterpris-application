package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Duration;

/**
 * 再設定のトークンの用途。用途ごとの期限と、繰り返しの依頼の抑えを数えるかを持つ（Bolt 38b。確認ポイント 15、SEC-08、IA-INV-12。抑えの値は
 * Bolt 38b2c）。抑えは {@link #RESET} の発行だけで数え、前の未使用のトークンの無効化は用途を問わない。US-19 の荷受人の招待（参照許可）とは
 * 別のもの。用途による出し分けは、外から定数を参照せず、この型のメソッドで問い合わせる（{@link #USER_INVITATION} を参照してよいのは招待の
 * ハンドラーだけ。ArchUnit の規則。Bolt 38b2c）。
 */
@ValueObject
public enum PasswordResetTokenPurpose {
    /** password の再設定（A-05 の依頼）。期限は 30 分。繰り返しの依頼の抑えを数える。 */
    RESET(Duration.ofMinutes(30), true),
    /**
     * 利用者の招待（システム管理者が登録した利用者への初回の設定のリンク。Bolt 38b2c から登録のコミットの後に発行する）。期限は 24 時間。抑えを
     * 数えない（招待の直後の本人の再設定を止めない）。
     */
    USER_INVITATION(Duration.ofHours(24), false);

    private final Duration validity;
    private final boolean throttlesRepeatedIssue;

    PasswordResetTokenPurpose(Duration validity, boolean throttlesRepeatedIssue) {
        this.validity = validity;
        this.throttlesRepeatedIssue = throttlesRepeatedIssue;
    }

    /** 発行から使えなくなるまでの期間。 */
    public Duration validity() {
        return validity;
    }

    /** 同じ用途の前の発行から 1 分以内の発行を抑えるか（IA-INV-12）。 */
    public boolean throttlesRepeatedIssue() {
        return throttlesRepeatedIssue;
    }
}
