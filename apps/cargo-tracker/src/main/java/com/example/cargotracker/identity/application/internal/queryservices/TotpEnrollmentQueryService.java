package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.RemainingRecoveryCodes;
import com.example.cargotracker.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * TOTP の登録の問い合わせ（Bolt 31b）。画面の層だけが呼ぶ（A-02 の画面が未登録の利用者を A-07 へ送る。Bolt 38d2 から、回復コードで
 * ログインした後の A-08 と印の知らせが残りの回復コードを読む）。認証の入口からは呼ばない
 * （層の規則 D-5 の認証の例外は commandservices に限る）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpEnrollmentQueryService {

    private final TotpCredentialRepository credentials;
    private final RecoveryCodeRepository recoveryCodes;

    public TotpEnrollmentQueryService(TotpCredentialRepository credentials, RecoveryCodeRepository recoveryCodes) {
        this.credentials = credentials;
        this.recoveryCodes = recoveryCodes;
    }

    /** TOTP を登録しているか。 */
    public boolean isEnrolled(UserId userId) {
        return credentials.findByUserId(userId).isPresent();
    }

    /**
     * 残りの回復コード（Bolt 38d2）。まだ使っていない回復コードの数。使用済みを除くのはリポジトリ（最大 10 行。ハッシュの照合はしない）。
     * 回復コードでログインした後は、ログインに使った 1 個を引いた数になる（照合の後のトランザクションで使用済みにしてから画面へ移す）。
     */
    public RemainingRecoveryCodes remainingRecoveryCodes(UserId userId) {
        return new RemainingRecoveryCodes(
                recoveryCodes.findUnusedByUserId(userId).size());
    }
}
