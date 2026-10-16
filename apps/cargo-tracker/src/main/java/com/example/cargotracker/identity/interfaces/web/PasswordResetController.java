package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetCompletion;
import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetCompletionService;
import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetRequestService;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.identity.domain.model.rules.PasswordPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A-05 password の再設定の依頼と A-06 password の再設定（Bolt 33・33a、US-18 AC8、SEC-04・SEC-05・SEC-08、UI 設計「A-05」「A-06」）。
 * どちらも未認証で開ける公開の画面。
 *
 * <p>A-05 は入力の形の誤り（空・形式）だけを示し、それ以外は登録の有無にかかわらず A-01 に戻して同じ知らせを出す（PRG。登録の有無を示さない）。
 *
 * <p>A-06: メールのリンク（この URL + / + トークン）を開くと、トークンを確かめて session にトークンの ID だけを置き（session の ID は変える）、トークンのない URL
 * （{@code /password-reset/new}）へ移す（PRG。トークンを以後の URL・Referer・session に残さない）。設定に成功したら、今のブラウザの認証と
 * session を捨てて新しい session で A-01 に移す（ログイン中のブラウザでも知らせが出る。ほかの端末の session はサービスが確定の後に消す）。
 * 使えないリンクは、どの理由でも同じ案内にする。2 つの欄に入れた値は応答に書き戻さない。
 */
@Controller
@RequestMapping(PasswordResetController.URL)
public class PasswordResetController {

    /** A-05 の URL（公開の URL。SecurityConfiguration の PUBLIC_PATHS）。メールのリンクは「この URL + / + トークン」。 */
    public static final String URL = SessionPaths.PASSWORD_RESET_URL;

    /** リンクと A-06 の URL の型（公開の URL。1 段だけ）。 */
    public static final String TOKEN_URL_PATTERN = SessionPaths.PASSWORD_RESET_TOKEN_URL_PATTERN;

    /** A-06 の URL（トークンのない URL）。 */
    static final String NEW_URL = URL + "/new";

    /** 依頼を受け付けた後に移る A-01 の URL（知らせの印。T-124）。 */
    static final String REQUESTED_URL = "/login?reset-requested";

    /** 再設定を終えた後に移る A-01 の URL（知らせの印。T-124）。 */
    static final String COMPLETED_URL = "/login?reset-completed";

    private static final String VIEW = "identity/password-reset";
    private static final String NEW_VIEW = "identity/password-reset-new";
    private static final String INVALID_VIEW = "identity/password-reset-invalid";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";
    private static final String EMAIL = "email";
    private static final String ERROR = "emailError";
    private static final String NEW_PASSWORD_ERROR = "newPasswordError";
    private static final String CONFIRM_ERROR = "confirmPasswordError";
    private static final String REDIRECT = "redirect:";

    private final PasswordResetRequestService requests;
    private final PasswordResetCompletionService completions;

    public PasswordResetController(PasswordResetRequestService requests, PasswordResetCompletionService completions) {
        this.requests = requests;
        this.completions = completions;
    }

    @GetMapping
    public String form() {
        return VIEW;
    }

    @PostMapping
    public String request(@RequestParam(name = EMAIL, defaultValue = "") String email, Model model) {
        Optional<EmailAddress> address = parse(email);
        if (address.isEmpty()) {
            model.addAttribute(EMAIL, email);
            model.addAttribute(
                    ERROR, email.isBlank() ? "メールアドレスを入力してください。" : "メールアドレスの形式が正しくありません。例: user@example.com");
            return VIEW;
        }
        requests.request(address.get());
        return REDIRECT + REQUESTED_URL;
    }

    /** メールのリンクを開く。使えればトークンの ID だけを session に置いて A-06 へ移す。トークンの値はログ・モデル・session に置かない。 */
    @GetMapping("/{token}")
    public String open(@PathVariable String token, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(REFERRER_POLICY, NO_REFERRER);
        Optional<UUID> tokenId = completions.open(token);
        if (tokenId.isEmpty()) {
            return INVALID_VIEW;
        }
        request.getSession().setAttribute(PasswordResetSession.TOKEN_ID, tokenId.get());
        // password を設定できる session になるので、ログインと同じく session の ID を変える（session 固定化を防ぐ。Bolt 33a の開発レビュー）
        request.changeSessionId();
        return REDIRECT + NEW_URL;
    }

    /** A-06 を開く。session のトークンがまだ使えればフォーム、使えなければ案内。 */
    @GetMapping("/new")
    public String newPassword(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(REFERRER_POLICY, NO_REFERRER);
        Optional<UUID> tokenId = tokenIdOf(request);
        if (tokenId.isEmpty() || !completions.isUsable(tokenId.get())) {
            forgetToken(request);
            return INVALID_VIEW;
        }
        addPolicy(model);
        return NEW_VIEW;
    }

    /** A-06 で新しい password を設定する。確認の不一致は画面で先に見て、方針の誤りと無効はサービスの結果で分ける。 */
    @PostMapping("/new")
    public String setPassword(
            @RequestParam(name = "newPassword", defaultValue = "") String newPassword,
            @RequestParam(name = "confirmPassword", defaultValue = "") String confirmPassword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(REFERRER_POLICY, NO_REFERRER);
        Optional<UUID> tokenId = tokenIdOf(request);
        if (tokenId.isEmpty()) {
            return INVALID_VIEW;
        }
        if (!newPassword.equals(confirmPassword)) {
            addPolicy(model);
            model.addAttribute(CONFIRM_ERROR, "確認の欄に、新しい password と同じものを入力してください。");
            return NEW_VIEW;
        }
        PasswordResetCompletion result = completions.complete(tokenId.get(), newPassword);
        return switch (result) {
            case PasswordResetCompletion.PolicyViolated(PasswordPolicyViolation violation) -> {
                addPolicy(model);
                model.addAttribute(NEW_PASSWORD_ERROR, messageOf(violation));
                yield NEW_VIEW;
            }
            case PasswordResetCompletion.Invalid _ -> {
                forgetToken(request);
                yield INVALID_VIEW;
            }
            case PasswordResetCompletion.Completed _ -> {
                // 今のブラウザの認証と session を捨て、新しい session で A-01 へ（Bolt 33a の開始準備の決定 6）
                new SecurityContextLogoutHandler().logout(request, response, null);
                request.getSession(true);
                yield REDIRECT + COMPLETED_URL;
            }
        };
    }

    /** 方針の誤りの文言（UI 設計「A-06」。数値は方針の定数から描く）。 */
    static String messageOf(PasswordPolicyViolation violation) {
        return switch (violation) {
            case TOO_SHORT -> PasswordPolicy.MIN_LENGTH + " 文字以上の password を入力してください。";
            case TOO_LONG -> PasswordPolicy.MAX_LENGTH + " 文字までの password を入力してください。";
            case TOO_MANY_BYTES ->
                "全角の文字を含めると長すぎます。全角の文字（かな・漢字など）は 1 文字を 3 文字分として数え、合わせて "
                        + PasswordPolicy.MAX_BYTES
                        + " 文字分までの password を入力してください。";
            case COMMON -> "この password はよく使われているため、使えません。ほかの人に推測されにくい password を入力してください。";
        };
    }

    private static void addPolicy(Model model) {
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("maxLength", PasswordPolicy.MAX_LENGTH);
        model.addAttribute("maxBytes", PasswordPolicy.MAX_BYTES);
    }

    private static Optional<UUID> tokenIdOf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return session.getAttribute(PasswordResetSession.TOKEN_ID) instanceof UUID id
                ? Optional.of(id)
                : Optional.empty();
    }

    private static void forgetToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PasswordResetSession.TOKEN_ID);
        }
    }

    /** 入力をメールアドレスとして読む。空・形式の誤り（UI 設計「A-05」）なら空。登録の有無は見ない。 */
    private static Optional<EmailAddress> parse(String email) {
        if (email.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EmailAddress.of(email));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
