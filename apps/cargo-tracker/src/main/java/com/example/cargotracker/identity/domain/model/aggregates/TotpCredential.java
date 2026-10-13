package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * TOTP の資格。利用者が登録した TOTP の暗号化した秘密と、最後に使った時間の刻みを持つ。
 * 最後に使った刻みは単調に増え、それ以下の刻みのコードは受け付けない（IA-INV-10。同じコードの再利用の拒否）。
 * 刻みは TOTP の時間の刻みの番号（epoch 秒 / 30）。保存はリポジトリの条件付きの更新で原子的に行う（ADR-011）。
 */
@AggregateRoot
public final class TotpCredential {

    /** 時間の刻みの長さ（RFC 6238 の既定。US-18）。 */
    public static final Duration STEP_DURATION = Duration.ofSeconds(30);

    /** 時間の窓。今の刻みの前後いくつまでのコードを受け付けるか（ADR-011。サーバーの時計は NTP で合わせる）。 */
    public static final int WINDOW = 1;

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

    /** 時刻の刻みの番号（epoch 秒 / 30）。 */
    public static long stepAt(UtcInstant at) {
        return Math.floorDiv(at.instant().getEpochSecond(), STEP_DURATION.toSeconds());
    }

    /**
     * コードが、今の刻みの前後 {@value #WINDOW} 個の刻みのどれかのコードに一致し、その刻みを受け付けるなら、その刻みを返す。
     * 比較は一定時間で行う（どこまで一致したかを時間から推測させない）。形の違うコードと、最後に使った刻み以下の刻みの
     * コードは受け付けない（失敗へ倒す。T-42）。受け付けた刻みは、リポジトリの条件付きの更新で使ったことにする。
     * 生成器には自分の暗号化した秘密を渡し、平文の秘密は扱わない（Bolt 31）。
     *
     * @param currentStep 今の刻みの番号（{@link #stepAt}）。0 以上（負なら失敗へ倒して例外）
     */
    public OptionalLong matchingStep(String code, long currentStep, TotpCodeGenerator generator) {
        requireStep(currentStep);
        if (code == null || code.isEmpty() || !code.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long matched = -1;
        for (long step = Math.max(0, currentStep - WINDOW); step <= currentStep + WINDOW; step++) {
            byte[] expected = generator.code(this.userId, this.secret, step).getBytes(StandardCharsets.US_ASCII);
            // 一致を見つけても窓のすべての刻みを比べ、比べる回数を一致の位置で変えない
            boolean equal = MessageDigest.isEqual(expected, given);
            boolean acceptable = accepts(step);
            // 比べた結果と受け付けるかは先に両方とも求めてあるので、ここでの短絡は比べる時間を変えない
            if (matched < 0 && equal && acceptable) {
                matched = step;
            }
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    private static long requireStep(long step) {
        if (step < 0) {
            throw new IllegalArgumentException("時間の刻みは 0 以上");
        }
        return step;
    }
}
