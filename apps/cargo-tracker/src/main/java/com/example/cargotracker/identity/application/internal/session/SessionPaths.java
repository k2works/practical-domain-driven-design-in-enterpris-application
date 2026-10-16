package com.example.cargotracker.identity.application.internal.session;

/**
 * 認証の入口・合成ルートと画面が共有する、ログインと session の手続きの URL（Bolt 33f で {@link SessionKeys} から分けた）。認証の入口
 * （{@code identity.infrastructure.security}）が移す先、合成ルート（{@code identity.infrastructure.config}）が公開する URL とメールに書く
 * URL、画面（{@code identity.interfaces.web}）のリンク・フォームの送信先・受ける URL が同じ値を参照する。ふるまいは持たない。URL（遷移先）
 * の関心を application 層に置くのは、内側の層から画面の層への依存を作らないため（定数はコンパイル時に埋め込まれ、層の規則には見えないが、
 * 意図した依存の向きを保つ。ADR-012 の決定 3）。値は {@code SessionPathsTest} が固定する。
 */
public final class SessionPaths {

    /** A-08 認証アプリの再登録の URL（回復コードでログインした後に移す。Bolt 33b）。 */
    public static final String RE_ENROLL_URL = "/account/authenticator";

    /** A-03 もう一度ログインしてください（失効の案内）の URL。 */
    public static final String EXPIRED_URL = "/session-expired";

    /** A-05 password の再設定の依頼の URL（公開。Bolt 33。Bolt 33f で画面の層から移した）。 */
    public static final String PASSWORD_RESET_URL = "/password-reset";

    /** 再設定のリンクの URL の形（公開。トークンが末尾に付く。Bolt 33a）。 */
    public static final String PASSWORD_RESET_TOKEN_URL_PATTERN = PASSWORD_RESET_URL + "/*";

    private SessionPaths() {}
}
