package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Optional;

/**
 * 再設定のトークンの用途。用途ごとの期限と、繰り返しの依頼の抑えを数えるかを持つ（Bolt 38b。確認ポイント 15、SEC-08、IA-INV-12。抑えの値は
 * Bolt 38b2c）。抑えは {@link #RESET} の発行だけで数え、前の未使用のトークンの無効化は用途を問わない。US-19 の荷受人の招待（参照許可）とは
 * 別のもの。用途による出し分けは、外から定数を参照せず、この型のメソッドで問い合わせる（{@link #USER_INVITATION} を参照してよいのは招待の
 * ハンドラーだけ。ArchUnit の規則。Bolt 38b2c）。Bolt 38b4 から、初回の password の設定か（A-06 の出し分けと監査）、契機の時刻で発行して
 * よいか（遅れて配られた契機）、後に発行された用途に譲るかを持つ。
 */
@ValueObject
public enum PasswordResetTokenPurpose {
    /** password の再設定（A-05 の依頼）。期限は 30 分。繰り返しの依頼の抑えを数える。初回の設定ではない。 */
    RESET(Duration.ofMinutes(30), true, false),
    /**
     * 利用者の招待（システム管理者が登録した利用者への初回の設定のリンク。Bolt 38b2c から登録のコミットの後に発行する）。期限は 24 時間。抑えを
     * 数えない（招待の直後の本人の再設定を止めない）。初回の設定で、契機（登録）から期限を過ぎたら発行せず、契機の後の本人の再設定に譲る
     * （Bolt 38b4）。
     */
    USER_INVITATION(Duration.ofHours(24), false, true);

    private final Duration validity;
    private final boolean throttlesRepeatedIssue;
    private final boolean setsInitialPassword;

    PasswordResetTokenPurpose(Duration validity, boolean throttlesRepeatedIssue, boolean setsInitialPassword) {
        this.validity = validity;
        this.throttlesRepeatedIssue = throttlesRepeatedIssue;
        this.setsInitialPassword = setsInitialPassword;
    }

    /** 発行から使えなくなるまでの期間。 */
    public Duration validity() {
        return validity;
    }

    /** 同じ用途の前の発行から 1 分以内の発行を抑えるか（IA-INV-12）。 */
    public boolean throttlesRepeatedIssue() {
        return throttlesRepeatedIssue;
    }

    /** 初回の password の設定か（A-06 の見出しと完了の知らせ、監査を選ぶ。Bolt 38b4）。 */
    public boolean setsInitialPassword() {
        return setsInitialPassword;
    }

    /**
     * 契機の時刻で発行してよいか（Bolt 38b4。遅れて配られた契機）。初回の設定は、契機から期限の前だけ（ちょうど期限は不可）。再設定は
     * いつも発行してよい（依頼の再配信は今までどおり）。
     */
    public boolean acceptsTriggerAt(UtcInstant triggeredAt, UtcInstant now) {
        return !setsInitialPassword
                || now.instant().isBefore(triggeredAt.instant().plus(validity));
    }

    /** 契機の後に発行されていたら譲る用途（Bolt 38b4。初回の設定は、契機の後の本人の再設定に譲る）。なければ空。 */
    public Optional<PasswordResetTokenPurpose> yieldsTo() {
        return setsInitialPassword ? Optional.of(RESET) : Optional.empty();
    }
}
