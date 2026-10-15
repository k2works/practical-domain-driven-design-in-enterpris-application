package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.EnrollmentResult;
import com.example.cargotracker.identity.application.internal.commandservices.EnrollmentStart;
import com.example.cargotracker.identity.application.internal.commandservices.TotpEnrollmentService;
import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.EnrollingUser;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A-07 認証アプリの登録（Bolt 31b、US-18 AC7、UI 設計「A-07」）。password の要素だけで開ける（全体の注釈の例外。ADR-011）。
 * 登録を始めると秘密を発行して QR と手入力のキーを示し、最初のコードで確かめて登録したら、回復コード 10 個をその応答で一度だけ示す
 * （PRG にしない。平文の回復コードをどこにも保存しないため）。登録は TOTP の要素を与えないので、登録の後は A-02 でログインを終える。
 * session には登録の途中の暗号文の文字列と登録を始めた時刻だけを置く（平文の秘密も表示の値も置かない。SEC-16）。登録済みの利用者
 * （2 つの要素がそろった主体を含む）は A-02 へ送る（画面の層は要素の権限を見ない。ArchUnit）。
 */
@Controller
@RequestMapping("/login/totp/enroll")
public class TotpEnrollmentController {

    /**
     * 登録の途中の暗号文を置く session の属性の名前（文字列で置く。{@code EncryptedSecret} は直列化できる型ではないため）。
     * 認証の入口（password の段の成功）がこの名前で消す（{@code SessionLifetime#ENROLLMENT_PENDING_SECRET} と同じ値。テストで守る）。
     */
    public static final String PENDING_SECRET = "cargotracker.session.enrollmentPendingSecret";

    /** 登録を始めた時刻を置く session の属性の名前（登録の途中の上限 15 分の起点。{@code SessionLifetime} と同じ値）。 */
    public static final String STARTED_AT = "cargotracker.session.enrollmentStartedAt";

    private static final String TO_TOTP = "redirect:/login/totp";

    private static final String TO_ENROLL = "redirect:/login/totp/enroll";
    private static final String FORM_VIEW = "identity/login-totp-enroll";

    /** 登録とログインを終える期限の長さ（{@code SessionLifetime.ENROLLMENT_LIFETIME} と同じ。テストで守る）。 */
    static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(15);

    private final TotpEnrollmentService enrollment;
    private final TotpEnrollmentQueryService enrollmentQuery;
    private final Clock clock;

    public TotpEnrollmentController(
            TotpEnrollmentService enrollment, TotpEnrollmentQueryService enrollmentQuery, Clock clock) {
        this.enrollment = enrollment;
        this.enrollmentQuery = enrollmentQuery;
        this.clock = clock;
    }

    @GetMapping
    public String form(PasswordVerifiedUser user, HttpSession session, Model model) {
        return start(user, session, model);
    }

    @PostMapping
    public String enroll(
            PasswordVerifiedUser user,
            HttpSession session,
            @RequestParam(name = "code", defaultValue = "") String code,
            Model model) {
        Optional<EncryptedSecret> pending = pendingSecret(session);
        if (pending.isEmpty()) {
            // 登録の途中の暗号文がない送信（登録の後の再送、登録を始めていない送信）。登録済みなら A-02、そうでなければ A-07 を開き直す
            return enrollmentQuery.isEnrolled(user.userId()) ? TO_TOTP : TO_ENROLL;
        }
        EnrollmentResult result = enrollment.complete(EnrollingUser.from(user), pending.get(), code.strip());
        return switch (result) {
            case EnrollmentResult.Enrolled(var recoveryCodes) -> {
                // 登録を始めた時刻は残す。A-02 で TOTP が通るまで同じ 15 分の上限で、回復コードを書き写す時間をとる（ADR-011 の決定 6）
                clearPending(session);
                model.addAttribute("recoveryCodes", recoveryCodes);
                model.addAttribute("deadline", deadline(session));
                yield "identity/login-totp-recovery-codes";
            }
            case EnrollmentResult.CodeRejected() -> {
                model.addAttribute("codeError", true);
                yield start(user, session, model);
            }
            case EnrollmentResult.SecretUnusable() -> {
                clearPending(session);
                model.addAttribute("restarted", true);
                yield start(user, session, model);
            }
            case EnrollmentResult.AlreadyEnrolled() -> {
                clearPending(session);
                yield TO_TOTP;
            }
        };
    }

    private String start(PasswordVerifiedUser user, HttpSession session, Model model) {
        EnrollmentStart start = enrollment.begin(EnrollingUser.from(user), pendingSecret(session));
        if (start instanceof EnrollmentStart.Started(EncryptedSecret secret, TotpProvisioning provisioning)) {
            session.setAttribute(PENDING_SECRET, secret.value());
            if (session.getAttribute(STARTED_AT) == null) {
                session.setAttribute(STARTED_AT, clock.instant());
            }
            model.addAttribute("deadline", deadline(session));
            model.addAttribute("qrSvg", provisioning.qrSvg());
            model.addAttribute("manualKey", provisioning.manualKey());
            return FORM_VIEW;
        }
        clearPending(session);
        return TO_TOTP;
    }

    /** 登録とログインを終える期限（登録を始めた時刻から 15 分）の表示。 */
    private static String deadline(HttpSession session) {
        return session.getAttribute(STARTED_AT) instanceof Instant startedAt
                ? DateTimeDisplay.customer(startedAt.plus(ENROLLMENT_LIFETIME))
                : "";
    }

    private static Optional<EncryptedSecret> pendingSecret(HttpSession session) {
        return session.getAttribute(PENDING_SECRET) instanceof String value && !value.isBlank()
                ? Optional.of(new EncryptedSecret(value))
                : Optional.empty();
    }

    private static void clearPending(HttpSession session) {
        session.removeAttribute(PENDING_SECRET);
    }
}
