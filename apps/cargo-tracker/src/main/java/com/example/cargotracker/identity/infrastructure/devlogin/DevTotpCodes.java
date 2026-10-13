package com.example.cargotracker.identity.infrastructure.devlogin;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.security.Base32;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 開発環境だけの A-02 の入力済みのコード（Bolt 31、ADR-011 の dev の 3 層目）。設定（{@code cargotracker.dev-login.accounts[n].totp-secret}）
 * の開発用の固定の秘密だけからコードを作り、利用者の表の秘密は読まない（復号しない）。開発用の利用者でない主体には何も作らない。
 * dev プロファイルでだけ組み立て、画面の層の関数の型 {@code DevTotpCodeSource} につなぐ（{@code DevLoginGuardConfiguration}。合成ルートの例外）。
 */
public final class DevTotpCodes {

    private final Map<String, EncryptedSecret> secrets = new HashMap<>();
    private final Map<String, UserId> owners = new HashMap<>();
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
            String email = normalize(account.email());
            UserId owner = syntheticOwner(email);
            try {
                owners.put(email, owner);
                secrets.put(email, cipher.encrypt(plain, owner));
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
        String key = normalize(email);
        return Optional.ofNullable(secrets.get(key))
                .map(secret -> generator.code(
                        owners.get(key), secret, TotpCredential.stepAt(new UtcInstant(clock.instant()))));
    }

    /**
     * この部品の中だけで使う作り物の持ち主（AAD）。利用者の表を読まない（ADR-011 の dev の 3 層目）ので、DB の利用者 ID とは別の、
     * メールアドレスから決まる ID にする（Bolt 31b の確認ポイント 1）。
     */
    private static UserId syntheticOwner(String email) {
        return new UserId(UUID.nameUUIDFromBytes(("dev-login:" + email).getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
