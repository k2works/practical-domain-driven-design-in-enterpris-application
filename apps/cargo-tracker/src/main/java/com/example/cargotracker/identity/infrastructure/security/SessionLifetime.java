package com.example.cargotracker.identity.infrastructure.security;

import java.time.Duration;

/**
 * session の期限（US-18 AC1・AC5、SEC-03、IA-INV-03）。無操作の期限は Spring Session の設定、発行からの上限は
 * {@link SessionLifetimeFilter} で判定する。
 */
public final class SessionLifetime {

    /** 2 つの要素がそろった session の上限。起点は TOTP が初めて通った時刻（Bolt 31、ADR-011）。 */
    public static final Duration MAX_LIFETIME = Duration.ofHours(8);

    /**
     * ログインの時刻（TOTP が初めて通った時刻）を置く session の属性の名前。クラスの移動や改名で既存の session の属性が読めなくならないよう、固定の値にする。
     */
    public static final String AUTHENTICATED_AT = "cargotracker.session.authenticatedAt";

    /** password の段だけの session の上限（Bolt 31。A-02 を開いたまま放置した session を残さない）。 */
    public static final Duration PASSWORD_STAGE_LIFETIME = Duration.ofMinutes(5);

    /** password を確かめた時刻を置く session の属性の名前（password の段だけの session の上限の起点。固定の値）。 */
    public static final String PASSWORD_VERIFIED_AT = "cargotracker.session.passwordVerifiedAt";

    /** 登録の途中（A-07 を開いた後）の password の段の上限（Bolt 31b。認証アプリの導入と読み取りに時間がかかるため）。 */
    public static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(15);

    /**
     * 登録を始めた時刻を置く session の属性の名前（登録の途中の上限の起点。A-07 の controller が置く。画面の層の
     * {@code TotpEnrollmentController.STARTED_AT} と同じ値で、テストで守る）。
     */
    public static final String ENROLLMENT_STARTED_AT = "cargotracker.session.enrollmentStartedAt";

    /** 登録の途中の暗号文を置く session の属性の名前（画面の層の {@code TotpEnrollmentController.PENDING_SECRET} と同じ値）。 */
    public static final String ENROLLMENT_PENDING_SECRET = "cargotracker.session.enrollmentPendingSecret";

    /**
     * 2 つの要素がそろった session の 8 時間の上限までの残りの秒数（{@code Long}）を置く request の属性の名前（Bolt 32b、ACC-06）。
     * {@link SessionLifetimeFilter} が置き、画面の層の {@code SessionTimerAdvice} が同じ値の定数で読む（画面の層は infrastructure に
     * 依存できない。名前の一致はテストで守る。Bolt 33b でまとめて置き場所を直す）。session の属性と分けるため接頭辞を変える。
     */
    public static final String LIFETIME_REMAINING_ATTRIBUTE = "cargotracker.request.sessionLifetimeRemainingSeconds";

    /**
     * ログインした後に戻れる業務の画面の GET のとき、その path（context path を含む）と query を置く request の属性の名前（Bolt 32e。警告の期限の到来の
     * 案内のリンクに使う）。{@link SessionLifetimeFilter} が置き、画面の層の {@code SessionTimerAdvice} が同じ値の定数で読む
     * （{@link #LIFETIME_REMAINING_ATTRIBUTE} と同じ形。Bolt 33b でまとめて直す）。
     */
    public static final String RETURN_PATH_ATTRIBUTE = "cargotracker.request.returnPath";

    /** 失効した session の request を移す先（A-03 再認証の案内）。 */
    public static final String EXPIRED_URL = "/session-expired";

    private SessionLifetime() {}
}
