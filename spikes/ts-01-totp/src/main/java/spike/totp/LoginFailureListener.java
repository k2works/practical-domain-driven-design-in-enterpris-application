package spike.totp;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

/**
 * password と TOTP の誤りを、Spring Security の認証の失敗のイベントから 1 か所で数える。本体では、同じ箇所から
 * 監査記録にも書く（IA-INV-08）。ロック中の拒否（LockedException）は数えない。
 */
@Component
public class LoginFailureListener {

    private final SpikeUsers users;

    public LoginFailureListener(SpikeUsers users) {
        this.users = users;
    }

    @EventListener
    public void on(AuthenticationFailureBadCredentialsEvent event) {
        users.recordFailure(event.getAuthentication().getName());
    }
}
