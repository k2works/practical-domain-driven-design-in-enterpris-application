package spike.totp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 開発環境だけのログインの入力済み（人の決定。2026-10-06）。dev プロファイルの設定にだけ値を書く。既定では空で、入力済みにしない。
 *
 * @param username A-01 に入れるメールアドレス
 * @param password A-01 に入れる password（開発用の固定の値。本物の秘密を使わない）
 * @param prefillTotp A-02 にその時点の TOTP のコードを入れるか
 */
@ConfigurationProperties("cargotracker.dev-login")
public record DevLoginProperties(String username, String password, boolean prefillTotp) {}
