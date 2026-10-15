package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;

/**
 * 回復コードでログインした印（Bolt 33b、US-18 AC9、UI 設計「A-08」）。認証の入口が回復コードが通ったときに session に置き（利用者の
 * ID と時刻）、A-08 の完了で画面が消す。password の段の成功（同じ session での利用者の切り替え）では認証の入口が消す。画面は印の利用者の
 * ID が今の主体と同じときだけ印として扱う（T-102）。印はログインから 15 分だけ有効にする（session を盗んだ者や離席中の端末に触れた者が、
 * password なしで認証アプリを登録し直せる時間を限る。Bolt 33b の開発レビューで human:kakimomokuri が決定）。
 */
public final class RecoveryLogin {

    /** 今の主体にとっての印の状態。 */
    enum State {
        /** 印がない（認証コードでログインした、別の利用者の印、登録し直した）。 */
        NONE,
        /** 印があり、A-08 で登録し直せる。 */
        ACTIVE,
        /** 印はあるが 15 分を過ぎた。登録し直すにはログインし直す。 */
        EXPIRED
    }

    private RecoveryLogin() {}

    static State stateOf(HttpSession session, AuthenticatedActor actor, Instant now) {
        if (session == null
                || actor == null
                || !(session.getAttribute(SessionKeys.RECOVERY_LOGIN_USER) instanceof String marked)
                || !marked.equals(actor.userId().value().toString())) {
            return State.NONE;
        }
        return session.getAttribute(SessionKeys.RECOVERY_LOGIN_AT) instanceof Instant at
                        && now.isBefore(at.plus(SessionPolicy.RECOVERY_LOGIN_LIFETIME))
                ? State.ACTIVE
                : State.EXPIRED;
    }

    /** 今の主体が、A-08 で登録し直せる印を持つか。 */
    static boolean isMarked(HttpSession session, AuthenticatedActor actor, Instant now) {
        return stateOf(session, actor, now) == State.ACTIVE;
    }

    /** 印を消す（A-08 で登録し直した）。 */
    static void clear(HttpSession session) {
        session.removeAttribute(SessionKeys.RECOVERY_LOGIN_USER);
        session.removeAttribute(SessionKeys.RECOVERY_LOGIN_AT);
    }
}
