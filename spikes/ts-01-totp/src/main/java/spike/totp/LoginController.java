package spike.totp;

import java.security.Principal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** ログイン（A-01）、認証コード（A-02）と、仮の業務の画面。 */
@Controller
public class LoginController {

    /** dev プロファイルでだけある。ほかの環境では空で、画面は入力済みにならない。 */
    private final ObjectProvider<DevLoginPrefill> devLogin;

    public LoginController(ObjectProvider<DevLoginPrefill> devLogin) {
        this.devLogin = devLogin;
    }

    @GetMapping("/login")
    public String login(Model model) {
        devLogin.ifAvailable(prefill -> {
            model.addAttribute("username", prefill.username());
            model.addAttribute("password", prefill.password());
        });
        return "login";
    }

    @GetMapping("/login/totp")
    public String totp(Principal principal, Model model) {
        if (principal != null) {
            devLogin.ifAvailable(prefill -> model.addAttribute("code", prefill.totpCode(principal.getName())));
        }
        return "totp";
    }

    @GetMapping("/staff")
    @ResponseBody
    public String staff(Principal principal) {
        return "業務の画面: " + principal.getName();
    }
}
