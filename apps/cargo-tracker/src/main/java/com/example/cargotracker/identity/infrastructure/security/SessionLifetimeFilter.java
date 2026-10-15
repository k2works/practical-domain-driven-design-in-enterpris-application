package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * session の上限（US-18 AC5、SEC-03）。TOTP が初めて通った時刻から 8 時間、password の段だけの session は password を確かめた
 * 時刻から 5 分（Bolt 31）。起点の時刻を session に置き、request ごとにアプリケーションの時計と比べる。
 * 認証の処理より前に置く（Bolt 13 の Try T-40）。認証済みなのにログインの時刻がない session も失効させる。上限を過ぎたら session を捨てて業務データを出さずに A-03 へ移す。
 * 古い session のままのログインの送信も A-03 へ移す（上限を過ぎた session を、ログインの送信で延命させない）。
 * 上限の前なら、2 つの要素がそろった session の 8 時間の残りを request の属性に置き、画面の session の警告に渡す（Bolt 32b）。
 * 8 時間の上限で捨てるときは開いていた業務の画面を新しい session に覚え、password の段・登録の途中の失効では覚えていた要求を
 * 持ち越す（Bolt 32e）。戻れる業務の画面の GET なら、戻り先（context path を含む path と query）を request の属性に置く（警告の
 * 期限の到来の案内のリンク。Bolt 32e）。
 */
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final ExpiredSessionRequestSaver requestSaver;

    public SessionLifetimeFilter(Clock clock, ExpiredSessionRequestSaver requestSaver) {
        this.clock = clock;
        this.requestSaver = requestSaver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        // 上限の判定と残りの秒数を同じ時刻で数える（境界で「上限の前なのに残り 0 秒」にしない）
        Instant now = clock.instant();
        if (session != null && expired(session, now)) {
            // 8 時間の上限なら、開いていた業務の画面を新しい session に覚える。password の段・登録の途中の失効は、ログインの
            // 手続きのやり直しなので、覚えていた要求を持ち越す（Bolt 32e）
            boolean fullyAuthenticated = fullyAuthenticated();
            SecurityContextHolder.clearContext();
            if (fullyAuthenticated) {
                requestSaver.discardAndRemember(request, response);
            } else {
                ExpiredSessionRequestSaver.invalidateCarryingSavedRequest(request);
            }
            response.sendRedirect(request.getContextPath() + SessionKeys.EXPIRED_URL);
            return;
        }
        if (session != null) {
            putLifetimeRemaining(request, session, now);
            if (requestSaver.returnable(request)) {
                request.setAttribute(SessionKeys.RETURN_PATH_ATTRIBUTE, returnPath(request));
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 2 つの要素がそろった session なら、8 時間の上限までの残りの秒数を request の属性に置く（Bolt 32b、ACC-06。画面の session の
     * 警告）。上限の判定と同じ起点と時計で数える。password の段・登録の途中・認証の前には置かない。
     */
    private static void putLifetimeRemaining(HttpServletRequest request, HttpSession session, Instant now) {
        if (!fullyAuthenticated()) {
            return;
        }
        if (session.getAttribute(SessionKeys.AUTHENTICATED_AT) instanceof Instant at) {
            Duration remaining = Duration.between(now, at.plus(SessionPolicy.MAX_LIFETIME));
            request.setAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE, remaining.toSeconds());
        }
    }

    /** 2 つの要素（password と TOTP）がそろった主体か。 */
    private static boolean fullyAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && hasAuthority(authentication, TotpAuthenticationToken.FACTOR_TOTP);
    }

    /** 戻り先（context path を含む path と query。そのままリンクにできる。Bolt 32e）。 */
    private static String returnPath(HttpServletRequest request) {
        return request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }

    /**
     * 上限を過ぎたか（Bolt 31）。2 つの要素がそろった session は TOTP が初めて通った時刻から 8 時間、password の段だけの session は
     * password を確かめた時刻から 5 分。どちらの上限を使うかは、session の時刻の属性ではなく認証の要素で選ぶ（利用者を切り替えた
     * session に前の利用者の時刻が残っても、password の段の上限を外さない。Bolt 31 の開発レビュー）。認証済みなのに起点の時刻が
     * ない session も失効として扱う（fail-closed。Bolt 14 レビュー）。配備の前の形の session（password の段の時刻がない）も失効する。
     */
    private static boolean expired(HttpSession session, Instant now) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        boolean totpPassed = hasAuthority(authentication, TotpAuthenticationToken.FACTOR_TOTP);
        if (totpPassed) {
            return expiredSince(session.getAttribute(SessionKeys.AUTHENTICATED_AT), SessionPolicy.MAX_LIFETIME, now);
        }
        // 登録の途中（A-07 を開いた後）だけ、登録を始めた時刻から 15 分（Bolt 31b）。それ以外の password の段は 5 分
        Object enrollmentStartedAt = session.getAttribute(SessionKeys.ENROLLMENT_STARTED_AT);
        if (enrollmentStartedAt != null) {
            return expiredSince(enrollmentStartedAt, SessionPolicy.ENROLLMENT_LIFETIME, now);
        }
        return expiredSince(
                session.getAttribute(SessionKeys.PASSWORD_VERIFIED_AT), SessionPolicy.PASSWORD_STAGE_LIFETIME, now);
    }

    private static boolean expiredSince(Object startedAt, Duration lifetime, Instant now) {
        return !(startedAt instanceof Instant at) || !now.isBefore(at.plus(lifetime));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
