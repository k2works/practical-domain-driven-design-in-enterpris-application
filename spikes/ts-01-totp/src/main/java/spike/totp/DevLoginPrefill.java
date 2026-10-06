package spike.totp;

import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 開発環境のログインの入力済み（Bolt 13 の確認ポイント 7）。dev プロファイルでだけ bean になり、ほかの環境ではこの部品自体がない。
 * 設定の値が誤って入っても、bean がなければ画面は入力済みにならない（守りの 1 層目）。
 */
@Component
@Profile("dev")
public class DevLoginPrefill {

    private final DevLoginProperties properties;
    private final SpikeUsers users;
    private final Clock clock;

    public DevLoginPrefill(DevLoginProperties properties, SpikeUsers users, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.clock = clock;
    }

    public String username() {
        return properties.username();
    }

    public String password() {
        return properties.password();
    }

    /** A-02 に入れるその時点のコード。開発用の固定の秘密から作る（TOTP の確認は省かない）。 */
    public String totpCode(String username) {
        return properties.prefillTotp() ? users.currentCode(username, clock.instant()) : null;
    }
}
