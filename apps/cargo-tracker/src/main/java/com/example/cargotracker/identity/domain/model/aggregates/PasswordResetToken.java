package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 再設定のトークン。password の再設定のリンクに入れる 1 回だけ使える値の発行の記録（IA-INV-12、SEC-08。Bolt 33）。
 * リンクの値（平文）を持たず、SHA-256 のハッシュだけを持つ。発行から用途の期限（再設定 30 分、利用者の招待 24 時間。Bolt 38b）で使えなく
 * なり、同じ利用者の新しい発行で前の未使用のトークンは用途を問わず使えなくなる。使う側（A-06）は Bolt 33a。使った時刻と無効にした時刻は 1 文の条件付きの更新で書くので、版を持たない。
 */
@AggregateRoot
public final class PasswordResetToken {

    /** 同じ利用者に発行し直せるまでの間隔（メールの爆撃と、前のリンクの無効化の悪用を抑える。Bolt 33 の確認ポイント 9）。 */
    public static final Duration REISSUE_INTERVAL = Duration.ofMinutes(1);

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UUID requestId;
    private final UserId userId;
    private final PasswordResetTokenPurpose purpose;
    private final String tokenHash;
    private final UtcInstant issuedAt;
    private final UtcInstant expiresAt;
    private final UtcInstant usedAt;
    private final UtcInstant invalidatedAt;

    @SuppressWarnings("java:S107") // 表の列をそろえて受ける（reconstitute と同じ並び）
    private PasswordResetToken(
            UUID id,
            UUID requestId,
            UserId userId,
            PasswordResetTokenPurpose purpose,
            String tokenHash,
            UtcInstant issuedAt,
            UtcInstant expiresAt,
            UtcInstant usedAt,
            UtcInstant invalidatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        if (tokenHash == null || !SHA256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("トークンのハッシュは SHA-256 の 16 進（小文字）64 文字にする");
        }
        this.tokenHash = tokenHash;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.instant().isAfter(issuedAt.instant())) {
            throw new IllegalArgumentException("期限は発行時刻より後にする");
        }
        if (usedAt != null && invalidatedAt != null) {
            throw new IllegalArgumentException("使ったトークンと無効にしたトークンは両立しない");
        }
        this.usedAt = usedAt;
        this.invalidatedAt = invalidatedAt;
    }

    /**
     * トークンを発行する。期限は発行から用途の期限（{@link PasswordResetTokenPurpose#validity()}。SEC-08）。
     *
     * @param purpose 用途（Bolt 38b）
     * @param requestId 再設定の依頼の ID
     * @param userId 利用者
     * @param tokenHash リンクの値の SHA-256 のハッシュ（{@link PasswordResetTokenHasher}）
     * @param issuedAt 発行時刻
     */
    public static PasswordResetToken issue(
            PasswordResetTokenPurpose purpose, UUID requestId, UserId userId, String tokenHash, UtcInstant issuedAt) {
        return new PasswordResetToken(
                UUID.randomUUID(),
                requestId,
                userId,
                purpose,
                tokenHash,
                issuedAt,
                new UtcInstant(issuedAt.instant().plus(purpose.validity())),
                null,
                null);
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    @SuppressWarnings("java:S107") // 表の列をそろえて受ける
    public static PasswordResetToken reconstitute(
            UUID id,
            UUID requestId,
            UserId userId,
            PasswordResetTokenPurpose purpose,
            String tokenHash,
            UtcInstant issuedAt,
            UtcInstant expiresAt,
            UtcInstant usedAt,
            UtcInstant invalidatedAt) {
        return new PasswordResetToken(
                id, requestId, userId, purpose, tokenHash, issuedAt, expiresAt, usedAt, invalidatedAt);
    }

    /**
     * 同じ利用者に新しいトークンを発行してよいか。前の発行から {@link #REISSUE_INTERVAL} たつまでは発行しない。
     *
     * @param lastIssuedAt その利用者の最後の発行時刻。発行がなければ空
     * @param now 今
     */
    public static boolean allowsIssue(Optional<UtcInstant> lastIssuedAt, UtcInstant now) {
        return lastIssuedAt
                .map(last -> !now.instant().isBefore(last.instant().plus(REISSUE_INTERVAL)))
                .orElse(true);
    }

    /** 使えるか（使っていない、無効にしていない、期限の前）。 */
    public boolean isUsable(UtcInstant now) {
        return usedAt == null && invalidatedAt == null && now.instant().isBefore(expiresAt.instant());
    }

    /** 無効にしたトークンを返す（同じ利用者の新しい発行で）。使った・無効にしたトークンは無効にしない。 */
    public PasswordResetToken invalidate(UtcInstant at) {
        if (usedAt != null || invalidatedAt != null) {
            throw new IllegalStateException("使った・無効にしたトークンは無効にしない");
        }
        return new PasswordResetToken(
                id, requestId, userId, purpose, tokenHash, issuedAt, expiresAt, null, Objects.requireNonNull(at, "at"));
    }

    public UUID id() {
        return id;
    }

    public UUID requestId() {
        return requestId;
    }

    public UserId userId() {
        return userId;
    }

    /** 用途（Bolt 38b）。 */
    public PasswordResetTokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public UtcInstant issuedAt() {
        return issuedAt;
    }

    public UtcInstant expiresAt() {
        return expiresAt;
    }

    public Optional<UtcInstant> usedAt() {
        return Optional.ofNullable(usedAt);
    }

    public Optional<UtcInstant> invalidatedAt() {
        return Optional.ofNullable(invalidatedAt);
    }

    /** ハッシュを文字列に出さない。 */
    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", requestId=" + requestId + ", userId=" + userId + "]";
    }
}
