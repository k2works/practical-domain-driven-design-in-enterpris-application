package com.example.cargotracker.identity.devlogin;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 開発環境だけの設定が dev の外で入っていたら起動させない（守りの 2 層目。ADR-011 の決定 3、ADR-012）。
 * 対象は、入力済みの設定（cargotracker.dev-login.*）と、開発用の利用者を入れる Flyway の場所（db/dev-data）。
 * staging や prod の設定に紛れ込んだときに、黙って無視するのではなく、起動の失敗で気づかせる。
 * 開発用の利用者がマイグレーションで入ってから止めても遅いため、Flyway の設定を組み立てるとき（マイグレーションの前）に確かめる
 * （{@code DevLoginGuardConfiguration}。Bolt 14 レビュー）。
 */
public final class DevLoginGuard {

    /** 開発用の企業と利用者を置く Flyway の場所。 */
    static final String DEV_DATA_LOCATION = "db/dev-data";

    private DevLoginGuard() {}

    /**
     * dev だけの設定が dev の外にあれば例外にする。
     *
     * @throws IllegalStateException dev の外、または staging・prod と一緒に dev だけの設定があるとき
     */
    public static void check(DevLoginProperties properties, Environment environment) {
        boolean devOnly = environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("staging | prod"));
        if (devOnly) {
            return;
        }
        if (properties.configured()) {
            throw new IllegalStateException("cargotracker.dev-login は dev プロファイルでだけ設定できる（staging・prod と併用しない）");
        }
        String locations = environment.getProperty("spring.flyway.locations", "");
        if (locations.contains(DEV_DATA_LOCATION)) {
            throw new IllegalStateException("開発用の利用者（" + DEV_DATA_LOCATION + "）は dev プロファイルでだけ入れられる");
        }
    }
}
