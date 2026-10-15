package com.example.cargotracker.notification.infrastructure.mail;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * メールの送信の守り（ADR-017。Bolt 33）。ログに出す送信は dev でだけ使い（{@code DevLoginGuard} と同じ式。staging・prod と併用しない）、
 * ステージング・本番では SMTP の設定がなければ起動を止める。黙ってメールを送らない（またはリンクをログに出す）ことを、起動の失敗で気づかせる。
 */
public final class NotificationMailGuard {

    private NotificationMailGuard() {}

    /** ログに出す送信を使ってよいか（dev かつ staging・prod と併用しない）。 */
    public static boolean logsMail(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("staging | prod"));
    }

    /**
     * 環境とメールの設定の組が正しいかを確かめる。
     *
     * @throws IllegalStateException dev と staging・prod を併用している、またはステージング・本番に SMTP の設定がないとき
     */
    public static void check(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("dev")) && !logsMail(environment)) {
            throw new IllegalStateException("メールをログに出す送信は dev プロファイルでだけ使える（staging・prod と併用しない）");
        }
        if (environment.acceptsProfiles(Profiles.of("staging | prod"))
                && !environment.containsProperty("spring.mail.host")) {
            throw new IllegalStateException("ステージング・本番ではメールの SMTP の設定（spring.mail.host）が要る");
        }
    }
}
