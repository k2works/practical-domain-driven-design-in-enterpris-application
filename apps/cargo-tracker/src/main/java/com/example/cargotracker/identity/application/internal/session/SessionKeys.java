package com.example.cargotracker.identity.application.internal.session;

/**
 * session・request の属性の名前と、認証の入口と画面が共有する URL（Bolt 33c）。認証の入口（{@code identity.infrastructure.security}）と画面
 * （{@code identity.interfaces.web}）の両方から参照する（画面の層は infrastructure に依存できず、Bolt 31c から 33b までは同じ値の 2 つの
 * 定数をテストで守っていた）。名前はクラスの移動や改名で動いている session の属性が読めなくならないよう、固定の値にする（{@code SessionKeysTest}）。
 * 期間は {@code SessionPolicy}（domain）に置く。
 */
public final class SessionKeys {

    /** ログインの時刻（TOTP か回復コードが初めて通った時刻。8 時間の上限の起点）を置く session の属性の名前。 */
    public static final String AUTHENTICATED_AT = "cargotracker.session.authenticatedAt";

    /** password を確かめた時刻（password の段だけの session の上限の起点）を置く session の属性の名前。 */
    public static final String PASSWORD_VERIFIED_AT = "cargotracker.session.passwordVerifiedAt";

    /** 認証アプリの登録を始めた時刻（登録の途中の上限の起点。A-07 が置く）を置く session の属性の名前。 */
    public static final String ENROLLMENT_STARTED_AT = "cargotracker.session.enrollmentStartedAt";

    /** 認証アプリの登録の途中の暗号文（A-07 が置き、password の段の成功で消す）を置く session の属性の名前。 */
    public static final String ENROLLMENT_PENDING_SECRET = "cargotracker.session.enrollmentPendingSecret";

    /** 回復コードでログインした印の利用者の ID（Bolt 33b）を置く session の属性の名前。 */
    public static final String RECOVERY_LOGIN_USER = "cargotracker.session.recoveryLoginUser";

    /** 回復コードでログインした時刻（印はこの時刻から 15 分だけ有効。Bolt 33b）を置く session の属性の名前。 */
    public static final String RECOVERY_LOGIN_AT = "cargotracker.session.recoveryLoginAt";

    /**
     * 2 つの要素がそろった session の 8 時間の上限までの残りの秒数（{@code Long}）を置く request の属性の名前（Bolt 32b）。session の属性と
     * 分けるため接頭辞を変える。
     */
    public static final String LIFETIME_REMAINING_ATTRIBUTE = "cargotracker.request.sessionLifetimeRemainingSeconds";

    /** ログインした後に戻れる業務の画面の GET のとき、その path と query を置く request の属性の名前（Bolt 32e）。 */
    public static final String RETURN_PATH_ATTRIBUTE = "cargotracker.request.returnPath";

    /** A-08 認証アプリの再登録の URL（回復コードでログインした後に移す。Bolt 33b）。 */
    public static final String RE_ENROLL_URL = "/account/authenticator";

    /** 失効した session の request を移す先（A-03 再認証の案内）。 */
    public static final String EXPIRED_URL = "/session-expired";

    private SessionKeys() {}
}
