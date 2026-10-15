package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * TOTP の登録の始め（Bolt 33d で初回の登録 {@link TotpEnrollmentService} と認証アプリの再登録 {@link TotpReEnrollmentService} の
 * 重複を寄せた）。登録の途中の暗号文を受けたら、同じ利用者の暗号文として読める限りそれを使い続け（再読み込みや 2 つのタブで秘密を
 * 変えない）、読めなければ新しく発行して、暗号文と表示の値を返す。Bean にせず、2 つのサービスが組み立てる。
 */
final class TotpProvisioningStarter {

    private final TotpSecretIssuer issuer;

    TotpProvisioningStarter(TotpSecretIssuer issuer) {
        this.issuer = Objects.requireNonNull(issuer, "issuer");
    }

    /**
     * 登録を始める。
     *
     * @param owner 登録する利用者
     * @param pending 登録の途中の暗号文（session に置いた値）
     * @param accountName 認証アプリに示す名前（メールアドレス）
     */
    Started start(UserId owner, Optional<EncryptedSecret> pending, String accountName) {
        if (pending.isPresent()) {
            Optional<TotpProvisioning> reused = provisioningOf(owner, pending.get(), accountName);
            if (reused.isPresent()) {
                return new Started(pending.get(), reused.get());
            }
        }
        EncryptedSecret secret = issuer.issue(owner);
        return new Started(secret, issuer.provisioning(owner, secret, accountName));
    }

    private Optional<TotpProvisioning> provisioningOf(UserId owner, EncryptedSecret secret, String accountName) {
        try {
            return Optional.of(issuer.provisioning(owner, secret, accountName));
        } catch (UnreadableCredentialException _) {
            // 別の利用者の暗号文・壊れた暗号文・知らない鍵の版は使い続けず、新しく発行する（失敗へ倒す。T-42）
            return Optional.empty();
        }
    }

    /** 始めた登録の暗号文と表示の値。 */
    record Started(EncryptedSecret secret, TotpProvisioning provisioning) {}
}
