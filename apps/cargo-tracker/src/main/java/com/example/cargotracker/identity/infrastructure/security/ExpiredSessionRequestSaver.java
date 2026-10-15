package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 失効した session の request の扱い（Bolt 32e、ACC-06、横断の受入条件の「再認証後に再開できる」、ADR-012）。新しい session を
 * 作り、開いていた業務の画面の GET（合成ルートが渡す {@code returnablePages}）なら要求を覚えて A-03 へ移す。TOTP が通った後の
 * 成功の扱い（{@code SavedRequestAwareAuthenticationSuccessHandler}。既定の {@link HttpSessionRequestCache}）がそこへ戻す。
 * 覚えるかどうかに関わらず新しい session を作る（作らないと、古い Cookie のまま A-03 も失効と判定され続ける）。
 * 送信（POST）は覚えない（送信の本体を session に入れない。送信の後の失効は Bolt 32f）。無操作の失効（Spring Security の
 * {@code invalidSessionStrategy}。CSRF のトークンのない送信の失効も通る）と 8 時間の上限（{@link SessionLifetimeFilter}）で使う。
 * 取消し（{@link SessionRevalidationFilter}）では使わない（開始準備の決定）。
 */
public class ExpiredSessionRequestSaver implements InvalidSessionStrategy {

    /**
     * 覚えた要求を置く session の属性の名前（{@link HttpSessionRequestCache} の既定。TOTP の成功の扱い・未認証の入口が同じ既定で
     * 読むので、この部品の cache にも明示して渡す。持ち越しで使う。一致は持ち越しの統合テストが見張る）。
     */
    static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    private final RequestMatcher returnablePages;
    private final RequestCache requestCache;

    public ExpiredSessionRequestSaver(RequestMatcher returnablePages) {
        this.returnablePages = returnablePages;
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(returnablePages);
        cache.setSessionAttrName(SAVED_REQUEST);
        this.requestCache = cache;
    }

    @Override
    public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 失効した session はもうないので、新しい session を作って覚える
        rememberInNewSession(request, response);
        response.sendRedirect(request.getContextPath() + SessionKeys.EXPIRED_URL);
    }

    /** 今の session を捨て、新しい session を作り、開いていた業務の画面の GET なら覚える（8 時間の上限。Bolt 32e）。 */
    public void discardAndRemember(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        rememberInNewSession(request, response);
    }

    private void rememberInNewSession(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        requestCache.saveRequest(request, response);
    }

    /** ログインした後に戻れる業務の画面の GET か（画面に渡す戻り先にも使う）。 */
    public boolean returnable(HttpServletRequest request) {
        return returnablePages.matches(request);
    }

    /**
     * session を捨てて新しい session を作り、捨てる前に覚えた要求があれば移す（Bolt 32e）。ログインの手続きのやり直し（認証コードの
     * 誤り、password の段・登録の途中の失効）で、戻る先を失わないようにする。移すのは覚えた要求の属性だけで、認証の情報は移さない。
     * 覚えた要求がなくても新しい session を作る（失効の扱いと同じく、古い Cookie のままの A-03 で失効の扱いを挟まない）。
     */
    public static void invalidateCarryingSavedRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object saved = session.getAttribute(SAVED_REQUEST);
        session.invalidate();
        HttpSession fresh = request.getSession(true);
        if (saved != null) {
            fresh.setAttribute(SAVED_REQUEST, saved);
        }
    }
}
