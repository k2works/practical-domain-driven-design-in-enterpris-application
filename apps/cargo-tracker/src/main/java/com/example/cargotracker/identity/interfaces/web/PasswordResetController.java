package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetRequestService;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
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

    /** A-05 の URL（公開の URL。SecurityConfiguration の PUBLIC_PATHS）。 */
    public static final String URL = "/password-reset";

    /** 依頼を受け付けた後に移る A-01 の URL（知らせの印。T-124）。 */
    static final String REQUESTED_URL = "/login?reset-requested";

    private static final String VIEW = "identity/password-reset";
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

    @PostMapping
    public String request(@RequestParam(name = EMAIL, defaultValue = "") String email, Model model) {
        Optional<String> error = formError(email);
        if (error.isPresent()) {
            model.addAttribute(EMAIL, email);
            model.addAttribute(ERROR, error.get());
            return VIEW;
        }
        service.request(EmailAddress.of(email));
        return "redirect:" + REQUESTED_URL;
    }

    /** 入力の形の誤り（UI 設計「A-05」の文言）。登録の有無は見ない。 */
    private static Optional<String> formError(String email) {
        if (email.isBlank()) {
            return Optional.of("メールアドレスを入力してください。");
        }
        try {
            EmailAddress.of(email);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of("メールアドレスの形式が正しくありません。例: user@example.com");
        }
    }
}
