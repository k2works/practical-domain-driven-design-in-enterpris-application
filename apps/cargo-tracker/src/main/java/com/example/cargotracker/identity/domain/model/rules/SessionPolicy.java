package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.time.Duration;

/**
 * session の方針。ログインの手続きと session の期間（US-18 AC1・AC5・AC7・AC9、SEC-03、IA-INV-03。Bolt 33c で、認証の入口と画面と
 * アプリケーションサービスに散っていた同じ値をまとめた）。期限の判定は使う側（認証の入口の {@code SessionLifetimeFilter}、画面、再登録の
 * サービス）が行い、ここは期間だけを持つ。無操作の期限は Spring Session の設定。
 */
@DomainRule
public final class SessionPolicy {

    /** 2 つの要素がそろった session の上限。起点は TOTP か回復コードが初めて通った時刻（Bolt 31、ADR-011）。 */
    public static final Duration MAX_LIFETIME = Duration.ofHours(8);

    /** password の段だけの session の上限（Bolt 31。A-02 を開いたまま放置した session を残さない）。 */
    public static final Duration PASSWORD_STAGE_LIFETIME = Duration.ofMinutes(5);

    /** 認証アプリの登録の途中（A-07 を開いた後）の password の段の上限（Bolt 31b。導入と読み取りに時間がかかるため）。 */
    public static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(15);

    /** 回復コードでログインした印の有効な長さ（Bolt 33b。password なしで再登録できる時間を限る）。 */
    public static final Duration RECOVERY_LOGIN_LIFETIME = Duration.ofMinutes(15);

    /** 認証アプリの再登録（A-08）を開いてから完了までの上限（Bolt 33b。A-07 の登録と同じ長さ）。 */
    public static final Duration RE_ENROLLMENT_LIFETIME = Duration.ofMinutes(15);

    private SessionPolicy() {}
}
