package spike.totp;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * TOTP の要素を確かめる。失敗は BadCredentialsException にし、認証の失敗のイベント（password と同じ経路）で回数を数える。
 * 成功したら要素の権限 FACTOR_TOTP を返す。password の要素と合わせるのはフィルター（mfaEnabled）が行う。
 */
public class TotpAuthenticationProvider implements AuthenticationProvider {

    private final SpikeUsers users;

    public TotpAuthenticationProvider(SpikeUsers users) {
        this.users = users;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        if (users.isLocked(username)) {
            throw new LockedException("ロックされている");
        }
        if (!users.verifySecondFactor(username, (String) authentication.getCredentials())) {
            throw new BadCredentialsException("認証コードが正しくない");
        }
        users.recordSuccess(username);
        return TotpAuthenticationToken.authenticated(
                username, FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TotpAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
