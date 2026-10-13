package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
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
        TotpCredential credential = found.get();
        long currentStep = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        OptionalLong matched = credential.matchingStep(code, currentStep, generator);
        if (matched.isPresent()) {
            return credentials.useStep(userId, matched.getAsLong())
                    ? TotpVerification.ACCEPTED
                    : TotpVerification.REJECTED_REUSED;
        }
        return wasAlreadyUsed(credential, code, currentStep)
                ? TotpVerification.REJECTED_REUSED
                : TotpVerification.REJECTED_MISMATCH;
    }

    /** TOTP を登録しているか（未登録の利用者を登録の案内へ送るため）。 */
    public boolean isEnrolled(UserId userId) {
        return credentials.findByUserId(userId).isPresent();
    }

    /**
     * 窓の中で一致したが、使った刻み以下だったか（監査記録の理由を分けるためだけに使う）。まだどの刻みも使っていない資格として
     * 照合し直す。拒否の後でだけ呼ぶので、通るときの照合の時間は変わらない。
     */
    private boolean wasAlreadyUsed(TotpCredential credential, String code, long currentStep) {
        TotpCredential unused =
                TotpCredential.reconstitute(credential.userId(), credential.secret(), 0, credential.enrolledAt());
        return unused.matchingStep(code, currentStep, generator).isPresent();
    }
}
