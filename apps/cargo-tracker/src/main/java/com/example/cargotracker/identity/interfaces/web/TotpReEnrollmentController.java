package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.ReEnrollmentResult;
import com.example.cargotracker.identity.application.internal.commandservices.ReEnrollmentStart;
import com.example.cargotracker.identity.application.internal.commandservices.TotpReEnrollmentService;
import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A-08 認証アプリの再登録（Bolt 33b、US-18 AC9、SEC-10、UI 設計「A-08」）。回復コードでログインした印があり、印の利用者が今の主体のときだけ
 * 開ける。印がなければ役割のホームへ移す（403 にしない）。A-07 と同じ形で、途中の暗号文と開いた時刻を session に置き（平文の秘密は置かない）、
 * 完了したら同じ応答で新しい回復コードを一度だけ示す。「後で登録する」は印を残して覚えた要求の画面か役割のホームへ移す。
 */
@Controller
@RequestMapping(SessionKeys.RE_ENROLL_URL)
public class TotpReEnrollmentController {

    /** 再登録の途中の暗号文を置く session の属性の名前（A-07 の途中と分ける）。 */
    static final String PENDING_SECRET = "cargotracker.session.reEnrollmentPendingSecret";

    /** 再登録を開いた時刻を置く session の属性の名前（15 分の上限の起点。再読み込みでは延ばさない）。 */
    static final String STARTED_AT = "cargotracker.session.reEnrollmentStartedAt";

    private static final String HOME = "redirect:/";
    private static final String FORM_VIEW = "identity/account-authenticator";
    private static final String CODES_VIEW = "identity/account-authenticator-codes";

    private final TotpReEnrollmentService reEnrollment;
    private final Clock clock;
    /** 覚えた要求を読む cache（失効の部品と同じ属性の名前。読み出しは matcher を使わない。Bolt 33c）。 */
    private final RequestCache requestCache = savedRequestCache();

    public TotpReEnrollmentController(TotpReEnrollmentService reEnrollment, Clock clock) {
        this.reEnrollment = reEnrollment;
        this.clock = clock;
    }

    @GetMapping
    public String form(AuthenticatedActor actor, HttpSession session, Model model) {
        if (!RecoveryLogin.isMarked(session, actor, clock.instant())) {
            return HOME;
        }
        return start(actor, session, model);
    }

    @PostMapping
    public String reEnroll(
            AuthenticatedActor actor,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(name = "code", defaultValue = "") String code,
            Model model) {
        if (!RecoveryLogin.isMarked(session, actor, clock.instant())) {
            return HOME;
        }
        Optional<EncryptedSecret> pending = pendingSecret(session);
        if (pending.isEmpty() || !(session.getAttribute(STARTED_AT) instanceof Instant startedAt)) {
            clearPending(session);
            model.addAttribute("restarted", true);
            return start(actor, session, model);
        }
        ReEnrollmentResult result = reEnrollment.complete(
                actor.userId(), pending.get(), code.strip(), new UtcInstant(startedAt), session.getId());
        return switch (result) {
            case ReEnrollmentResult.ReEnrolled(var recoveryCodes) -> {
                clearPending(session);
                RecoveryLogin.clear(session);
                addLayout(actor, model);
                model.addAttribute("recoveryCodes", recoveryCodes);
                yield CODES_VIEW;
            }
            case ReEnrollmentResult.CodeRejected() -> {
                model.addAttribute("codeError", true);
                yield start(actor, session, model);
            }
            case ReEnrollmentResult.Expired() -> {
                clearPending(session);
                model.addAttribute("expired", true);
                yield start(actor, session, model);
            }
            case ReEnrollmentResult.SecretUnusable() -> {
                clearPending(session);
                model.addAttribute("restarted", true);
                yield start(actor, session, model);
            }
            case ReEnrollmentResult.Superseded() -> {
                // ほかの画面・二度押しで先に登録し直していた。新しい回復コードは先の画面に一度だけ示した
                clearPending(session);
                RecoveryLogin.clear(session);
                yield HOME;
            }
            case ReEnrollmentResult.Unavailable() -> {
                clearPending(session);
                yield HOME;
            }
        };
    }

    /** 後で登録する。印を残して、覚えた要求の画面か役割のホームへ移す（印がなければ役割のホーム）。 */
    @PostMapping("/later")
    public String later(
            AuthenticatedActor actor, HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        if (!RecoveryLogin.isMarked(session, actor, clock.instant())) {
            return HOME;
        }
        return "redirect:" + savedOrHome(request, response);
    }

    /**
     * 新しい回復コードの画面の「続ける」。覚えた要求の画面か役割のホームへ移す（GET のフォームは action の query を捨てるので、POST で
     * 受けてサーバーが redirect する。Bolt 33b の開発レビュー）。
     */
    @PostMapping("/continue")
    public String proceed(HttpServletRequest request, HttpServletResponse response) {
        return "redirect:" + savedOrHome(request, response);
    }

    private String start(AuthenticatedActor actor, HttpSession session, Model model) {
        if (session.getAttribute(STARTED_AT) instanceof Instant started
                && SessionPolicy.isExpired(
                        new UtcInstant(started),
                        new UtcInstant(clock.instant()),
                        SessionPolicy.RE_ENROLLMENT_LIFETIME)) {
            // 期限を過ぎた途中の値は使わず、新しいキーと期限で始め直す
            clearPending(session);
        }
        ReEnrollmentStart start = reEnrollment.begin(actor.userId(), pendingSecret(session));
        if (start instanceof ReEnrollmentStart.Started(EncryptedSecret secret, TotpProvisioning provisioning)) {
            session.setAttribute(PENDING_SECRET, secret.value());
            if (!(session.getAttribute(STARTED_AT) instanceof Instant)) {
                session.setAttribute(STARTED_AT, clock.instant());
            }
            addLayout(actor, model);
            Instant deadline = ((Instant) session.getAttribute(STARTED_AT)).plus(SessionPolicy.RE_ENROLLMENT_LIFETIME);
            model.addAttribute(
                    "deadline",
                    isShipper(actor) ? DateTimeDisplay.customer(deadline) : DateTimeDisplay.staff(deadline));
            model.addAttribute("qrSvg", provisioning.qrSvg());
            model.addAttribute("manualKey", provisioning.manualKey());
            return FORM_VIEW;
        }
        clearPending(session);
        return HOME;
    }

    /** ログインした画面のレイアウト（役割で選ぶ）。A-08 には印の知らせを出さない（本文が同じことを言う）。 */
    private static void addLayout(AuthenticatedActor actor, Model model) {
        model.addAttribute("layout", isShipper(actor) ? "layout/customer" : "layout/staff");
        model.addAttribute(RecoveryLoginAdvice.NOTICE, null);
    }

    private static boolean isShipper(AuthenticatedActor actor) {
        return actor.roles().contains(Role.SHIPPER);
    }

    /** 覚えた要求の URL（サーバーが覚えたもの。利用者の入力から作らない）。なければルート（役割のホームへ振り分ける）。 */
    private String savedOrHome(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return "/";
        }
        requestCache.removeRequest(request, response);
        return saved.getRedirectUrl();
    }

    private static RequestCache savedRequestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setSessionAttrName(SessionKeys.SAVED_REQUEST);
        return cache;
    }

    private static Optional<EncryptedSecret> pendingSecret(HttpSession session) {
        return session.getAttribute(PENDING_SECRET) instanceof String value && !value.isBlank()
                ? Optional.of(new EncryptedSecret(value))
                : Optional.empty();
    }

    private static void clearPending(HttpSession session) {
        session.removeAttribute(PENDING_SECRET);
        session.removeAttribute(STARTED_AT);
    }

    /** コードの欄の桁数（{@code pattern}・{@code maxlength}）。テンプレートは静的なクラスを参照しないのでモデルで渡す（Bolt 33d）。 */
    @ModelAttribute("codeDigits")
    int codeDigits() {
        return TotpCredential.DIGITS;
    }
}
