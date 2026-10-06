package spike.totp;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 入力済みの設定（cargotracker.dev-login.*）が dev プロファイルの外で入っていたら起動させない（守りの 2 層目）。
 * staging や prod の設定に紛れ込んだときに、黙って無視するのではなく、起動の失敗で気づかせる。
 */
@Component
public class DevLoginGuard {

    public DevLoginGuard(DevLoginProperties properties, Environment environment) {
        boolean configured = properties.username() != null || properties.password() != null || properties.prefillTotp();
        boolean devOnly = environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("staging | prod"));
        if (configured && !devOnly) {
            throw new IllegalStateException(
                    "cargotracker.dev-login は dev プロファイルでだけ設定できる（staging・prod と併用しない）");
        }
    }
}
