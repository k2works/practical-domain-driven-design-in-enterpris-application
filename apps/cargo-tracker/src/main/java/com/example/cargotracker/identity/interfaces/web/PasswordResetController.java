package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetRequestService;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A-05 password の再設定の依頼（Bolt 33、US-18 AC8、SEC-08、UI 設計「A-05」）。未認証で開ける公開の画面。入力の形の誤り（空・形式）だけを
 * A-05 で示し、それ以外は登録の有無にかかわらず A-01 に戻して同じ知らせを出す（PRG。登録の有無を示さない）。ログイン済みや password の段の
 * session で開いても、入力したアドレスで扱い、session の利用者は変えない。
 */
@Controller
@RequestMapping(PasswordResetController.URL)
public class PasswordResetController {

    /** A-05 の URL（公開の URL。SecurityConfiguration の PUBLIC_PATHS）。メールのリンクは「この URL + / + トークン」（A-06）。 */
    public static final String URL = "/password-reset";

    /** A-06 の URL の型（公開の URL。トークンは 1 つの path の要素）。 */
    public static final String TOKEN_URL_PATTERN = URL + "/*";

    /** 依頼を受け付けた後に移る A-01 の URL（知らせの印。T-124）。 */
    static final String REQUESTED_URL = "/login?reset-requested";

    private static final String VIEW = "identity/password-reset";
    private static final String PENDING_VIEW = "identity/password-reset-pending";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";
    private static final String EMAIL = "email";
    private static final String ERROR = "emailError";

    private final PasswordResetRequestService service;

    public PasswordResetController(PasswordResetRequestService service) {
        this.service = service;
    }

    @GetMapping
    public String form() {
        return VIEW;
    }

    /**
     * 仮の A-06（Bolt 33a まで。2026-10-15 の人の決定）。メールのリンクを開くと「準備中」だけを示す。トークンは読まず、公開の URL なので
     * session にも覚えない。リンクの URL を次の要求の Referer に出さない。Bolt 33a で本物の A-06 に置き換える。
     */
    @GetMapping("/{token}")
    public String pending(HttpServletResponse response) {
        response.setHeader(REFERRER_POLICY, NO_REFERRER);
        return PENDING_VIEW;
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
        service.request(address.get());
        return "redirect:" + REQUESTED_URL;
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
