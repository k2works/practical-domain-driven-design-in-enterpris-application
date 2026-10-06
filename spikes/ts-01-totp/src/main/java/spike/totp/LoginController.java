package spike.totp;

import java.security.Principal;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** ログイン（A-01）、認証コード（A-02）と、仮の業務の画面。 */
@Controller
public class LoginController {

    private final DevLoginProperties devLogin;
    private final SpikeUsers users;
    private final Clock clock;

    public LoginController(DevLoginProperties devLogin, SpikeUsers users, Clock clock) {
        this.devLogin = devLogin;
        this.users = users;
        this.clock = clock;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("username", devLogin.username());
        model.addAttribute("password", devLogin.password());
        return "login";
    }

    @GetMapping("/login/totp")
    public String totp(Principal principal, Model model) {
        model.addAttribute(
                "code",
                devLogin.prefillTotp() && principal != null ? users.currentCode(principal.getName(), clock.instant()) : null);
        return "totp";
    }

    @GetMapping("/staff")
    @ResponseBody
    public String staff(Principal principal) {
        return "業務の画面: " + principal.getName();
    }
}
