package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * TOTP の資格。利用者が登録した TOTP の暗号化した秘密と、最後に使った時間の刻みを持つ。
 * 最後に使った刻みは単調に増え、それ以下の刻みのコードは受け付けない（IA-INV-10。同じコードの再利用の拒否）。
 * 刻みは TOTP の時間の刻みの番号（epoch 秒 / 30）。保存はリポジトリの条件付きの更新で原子的に行う（ADR-011）。
 */
@AggregateRoot
public final class TotpCredential {

    private final UserId userId;
    private final EncryptedSecret secret;
    private final long lastUsedStep;
    private final UtcInstant enrolledAt;

    private TotpCredential(UserId userId, EncryptedSecret secret, long lastUsedStep, UtcInstant enrolledAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.lastUsedStep = requireStep(lastUsedStep);
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    /** TOTP を登録する。まだどの刻みも使っていない。 */
    public static TotpCredential enroll(UserId userId, EncryptedSecret secret, UtcInstant enrolledAt) {
        return new TotpCredential(userId, secret, 0, enrolledAt);
    }

    /** 保存されている状態から TOTP の資格を組み立てる（リポジトリが使う）。 */
    public static TotpCredential reconstitute(
            UserId userId, EncryptedSecret secret, long lastUsedStep, UtcInstant enrolledAt) {
        return new TotpCredential(userId, secret, lastUsedStep, enrolledAt);
    }

    public UserId userId() {
        return userId;
    }

    public EncryptedSecret secret() {
        return secret;
    }

    public long lastUsedStep() {
        return lastUsedStep;
    }

    public UtcInstant enrolledAt() {
        return enrolledAt;
    }

    /** その刻みのコードを受け付けるか。最後に使った刻みより後だけを受け付ける。 */
    public boolean accepts(long step) {
        return requireStep(step) > lastUsedStep;
    }

    /** その刻みを使った資格を返す。受け付けない刻みは使えない。 */
    public TotpCredential use(long step) {
        if (!accepts(step)) {
            throw new IllegalStateException("最後に使った刻み以下の刻みは使えない");
        }
        return new TotpCredential(userId, secret, step, enrolledAt);
    }

    private static long requireStep(long step) {
        if (step < 0) {
            throw new IllegalArgumentException("時間の刻みは 0 以上");
        }
        return step;
    }
}
