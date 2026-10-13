package com.example.cargotracker.identity.infrastructure.devlogin;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.security.Base32;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 開発環境だけの A-02 の入力済みのコード（Bolt 31、ADR-011 の dev の 3 層目）。設定（{@code cargotracker.dev-login.accounts[n].totp-secret}）
 * の開発用の固定の秘密だけからコードを作り、利用者の表の秘密は読まない（復号しない）。開発用の利用者でない主体には何も作らない。
 * dev プロファイルでだけ組み立て、画面の層の関数の型 {@code DevTotpCodeSource} につなぐ（{@code DevLoginGuardConfiguration}。合成ルートの例外）。
 */
public final class DevTotpCodes {

    private final Map<String, EncryptedSecret> secrets = new HashMap<>();
    private final TotpCodeGenerator generator;
    private final Clock clock;

    public DevTotpCodes(
            DevLoginProperties properties, AesGcmSecretCipher cipher, TotpCodeGenerator generator, Clock clock) {
        this.generator = generator;
        this.clock = clock;
        for (DevLoginProperties.Account account : properties.accounts()) {
            if (account.totpSecret() == null || account.totpSecret().isBlank()) {
                continue;
            }
            // 生成器は暗号文を受けるので、設定の平文の秘密をこの部品の中で暗号化し、平文の配列は消す
            byte[] plain = Base32.decode(account.totpSecret());
            try {
                secrets.put(normalize(account.email()), cipher.encrypt(plain));
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    /** 開発用の利用者のその時点のコード。開発用の利用者でなければ空。 */
    public Optional<String> codeFor(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(secrets.get(normalize(email)))
                .map(secret -> generator.code(secret, TotpCredential.stepAt(new UtcInstant(clock.instant()))));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
