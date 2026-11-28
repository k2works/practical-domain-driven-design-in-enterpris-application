package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpMatch;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * TOTP の確認。TOTP を確かめる入力ポート（Bolt 31、US-18 AC1、IA-INV-10）。利用者の TOTP の資格を引き、前後 1 つの刻みで照合し、一致した
 * 刻みを条件付きの更新で使う。同じ刻みの同時の使用は 1 つだけが通り、後は再利用として拒む。Spring Security の認証の入口
 * （{@code identity.infrastructure.security}）から呼ばれる（層の規則 D-5 の認証の例外。ADR-012）。
 *
 * <p>刻みの使用は 1 文の条件付きの更新で原子的なので、このサービスはトランザクションを開かない（呼び出し側のトランザクションに
 * 加わる）。{@code @Service} は JIG がユースケースとして読むための印で、組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpVerificationService {

    private final TotpCredentialRepository credentials;
    private final TotpCodeGenerator generator;
    private final Clock clock;

    public TotpVerificationService(TotpCredentialRepository credentials, TotpCodeGenerator generator, Clock clock) {
        this.credentials = credentials;
        this.generator = generator;
        this.clock = clock;
    }

    /**
     * 利用者の入れたコードを確かめる。拒否の種類は監査記録の理由にだけ使い、利用者には同じ文言を示す（AC2）。
     * 資格のない利用者は拒む（失敗へ倒す。T-42）。
     */
    public TotpVerification verify(UserId userId, String code) {
        Optional<TotpCredential> found = credentials.findByUserId(userId);
        if (found.isEmpty()) {
            return TotpVerification.REJECTED_NOT_ENROLLED;
        }
        long currentStep = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        TotpCredential credential = found.get();
        return switch (credential.match(code, currentStep, generator)) {
            // 同時の確かめで後になったとき・照合の後に資格を置き換えていたとき（Bolt 33b）は、条件付きの更新が失敗する（再利用として拒む）
            case TotpMatch.Matched(long step) ->
                credentials.useStep(userId, credential.secret(), step)
                        ? TotpVerification.ACCEPTED
                        : TotpVerification.REJECTED_REUSED;
            case TotpMatch.Reused() -> TotpVerification.REJECTED_REUSED;
            case TotpMatch.Mismatch() -> TotpVerification.REJECTED_MISMATCH;
        };
    }
}
