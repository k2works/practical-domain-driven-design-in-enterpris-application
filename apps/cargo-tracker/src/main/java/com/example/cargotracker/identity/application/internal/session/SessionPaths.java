package com.example.cargotracker.identity.application.internal.session;

/**
 * 認証の入口と画面が共有する URL（Bolt 33f で {@link SessionKeys} から分けた）。認証の入口（{@code identity.infrastructure.security}）が
 * 移す先と、画面（{@code identity.interfaces.web}）のリンクとフォームの送信先が同じ値を参照する。ふるまいは持たない。Web の経路の関心を
 * application 層に置くのは、両方から参照できる置き場所がここだけだから（{@code interfaces} はどの層からも参照されない。ADR-012 の決定 3）。
 * 値は {@code SessionPathsTest} が固定する。
 */
public final class SessionPaths {

    /** A-08 認証アプリの再登録の URL（回復コードでログインした後に移す。Bolt 33b）。 */
    public static final String RE_ENROLL_URL = "/account/authenticator";

    /** A-03 もう一度ログインしてください（失効の案内）の URL。 */
    public static final String EXPIRED_URL = "/session-expired";

    private SessionPaths() {}
}
