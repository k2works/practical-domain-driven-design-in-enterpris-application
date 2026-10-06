package com.example.cargotracker.identity.devlogin;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 入力済みの設定（cargotracker.dev-login.*）が dev の外で入っていたら起動させない（守りの 2 層目。ADR-011 の決定 3、ADR-012）。
 * staging や prod の設定に紛れ込んだときに、黙って無視するのではなく、起動の失敗で気づかせる。
 */
@Component
public final class DevLoginGuard {

    public DevLoginGuard(DevLoginProperties properties, Environment environment) {
        check(properties, environment);
    }

    /** Red の骨組み。 */
    public static void check(DevLoginProperties properties, Environment environment) {
        if (properties == properties) {
            return;
        }
        boolean devOnly = environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("staging | prod"));
        if (properties.configured() && !devOnly) {
            throw new IllegalStateException("cargotracker.dev-login は dev プロファイルでだけ設定できる（staging・prod と併用しない）");
        }
    }
}
