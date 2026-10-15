package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpSession;

/**
 * 回復コードでログインした印（Bolt 33b、US-18 AC9、UI 設計「A-08」）。認証の入口が回復コードが通ったときに session に置き（値は利用者の
 * ID）、A-08 の完了で画面が消す。password の段の成功（同じ session での利用者の切り替え）では認証の入口が消す。画面は印の利用者の ID が
 * 今の主体と同じときだけ印として扱う（T-102）。
 */
public final class RecoveryLogin {

    /**
     * 印を置く session の属性の名前。認証の入口の {@code SessionLifetime.RECOVERY_LOGIN_USER} と同じ値（画面の層は infrastructure に
     * 依存できないので、2 つの定数で持ち一致をテストで守る。Bolt 33c でまとめて置き場所を直す）。
     */
    public static final String MARK = "cargotracker.session.recoveryLoginUser";

    private RecoveryLogin() {}

    /** 今の主体が回復コードでログインした印を持つか。 */
    static boolean isMarked(HttpSession session, AuthenticatedActor actor) {
        return session != null
                && actor != null
                && session.getAttribute(MARK) instanceof String marked
                && marked.equals(actor.userId().value().toString());
    }

    /** 印を消す（A-08 で登録し直した）。 */
    static void clear(HttpSession session) {
        session.removeAttribute(MARK);
    }
}
