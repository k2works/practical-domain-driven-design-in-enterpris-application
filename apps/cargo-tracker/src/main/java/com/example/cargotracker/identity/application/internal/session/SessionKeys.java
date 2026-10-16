package com.example.cargotracker.identity.application.internal.session;

/**
 * session・request の属性の名前（Bolt 33c。URL は Bolt 33f から {@link SessionPaths}）。認証の入口（{@code identity.infrastructure.security}）と画面
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

    /**
     * ログインの前・失効の前に開いていた画面の要求（覚えた要求）を置く session の属性の名前。Spring Security の
     * {@code HttpSessionRequestCache} の既定の名前で、未認証の入口と回復コード・TOTP の成功の扱いが既定で読む。失効の部品
     * （{@code ExpiredSessionRequestSaver}）と A-08 の画面は、この名前を明示した cache を使う（Bolt 33c。Spring Security の既定の cache の
     * 範囲を変えないよう、Bean は作らず名前だけを共有する）。
     */
    public static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    private SessionKeys() {}
}
