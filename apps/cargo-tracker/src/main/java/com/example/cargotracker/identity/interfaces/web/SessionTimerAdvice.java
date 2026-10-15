package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
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
     * 無操作の期限の秒数、8 時間の上限までの残りの秒数、戻り先（GET で開いた業務の画面の path と query。送信の後の画面などでは
     * null。Bolt 32e）。
     */
    public record SessionTimer(long idleSeconds, long lifetimeRemainingSeconds, String returnPath, String ownerKey) {}

    @ModelAttribute("sessionTimer")
    public SessionTimer sessionTimer(HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        if (!(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE) instanceof Long lifetimeRemaining)) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return new SessionTimer(
                session.getMaxInactiveInterval(),
                lifetimeRemaining,
                request.getAttribute(SessionKeys.RETURN_PATH_ATTRIBUTE) instanceof String path ? path : null,
                actor.map(each -> ownerKey(each.userId())).orElse(null));
    }

    /**
     * 入力の控え（Bolt 32f）を書いた利用者を確かめる鍵。利用者の ID の SHA-256 の先頭 16 文字（URL で使える Base64）。画面に内部の
     * 識別子（UUID）を出さない（BR-07 の確かめ）ための一方向の値で、同じ利用者かどうかだけが分かる。
     */
    public static String ownerKey(UserId userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.value().toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest)
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えない", e);
        }
    }
}
