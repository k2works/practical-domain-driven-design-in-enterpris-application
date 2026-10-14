package com.example.cargotracker.identity.interfaces.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * session の警告（Bolt 32b、ACC-06、UI 設計の共通部品「session の警告」）のため、ログインした画面のモデルに無操作の期限の秒数と
 * 8 時間の上限までの残りの秒数を置く。共通のレイアウトがこれを警告の部分の {@code data-} 属性に書き、ブラウザが数える。
 * 8 時間の残りは認証の入口（{@code SessionLifetimeFilter}）が 2 つの要素のそろった session でだけ request の属性に置くので、
 * その属性があるときだけ置く（password の段・認証の前の画面には置かない）。session を作らないよう {@code getSession(false)} で読む。
 */
@ControllerAdvice
public class SessionTimerAdvice {

    /**
     * 8 時間の残りの秒数の request の属性の名前。認証の入口の {@code SessionLifetime.LIFETIME_REMAINING_ATTRIBUTE} と同じ値
     * （画面の層は infrastructure に依存できないので、2 つの定数で持ち一致をテストで守る。Bolt 33b でまとめて直す）。
     */
    public static final String LIFETIME_REMAINING_ATTRIBUTE = "cargotracker.request.sessionLifetimeRemainingSeconds";

    /** 無操作の期限の秒数と、8 時間の上限までの残りの秒数。 */
    public record SessionTimer(long idleSeconds, long lifetimeRemainingSeconds) {}

    @ModelAttribute("sessionTimer")
    public SessionTimer sessionTimer(HttpServletRequest request) {
        if (!(request.getAttribute(LIFETIME_REMAINING_ATTRIBUTE) instanceof Long lifetimeRemaining)) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return new SessionTimer(session.getMaxInactiveInterval(), lifetimeRemaining);
    }
}
