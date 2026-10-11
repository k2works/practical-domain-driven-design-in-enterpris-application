package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 回復コード。TOTP の端末を失ったときに一度だけ使えるコード。ハッシュだけを持ち、平文を持たない（IA-INV-11、SEC-09）。
 * 自分のリポジトリを持ち、消費を 1 文の条件付きの更新で原子的に行うため、小さな集約とする。使ったコードは
 * 使った時刻を入れて残す。発行のたびに利用者のコードを置き換える（発行の流れは Bolt 31）。
 */
@AggregateRoot
public final class RecoveryCode {

    /** 利用者ごとに発行する数（SEC-09）。 */
    public static final int CODES_PER_USER = 10;

    private final UUID id;
    private final UserId userId;
    private final String codeHash;
    private final UtcInstant usedAt;
    private final UtcInstant issuedAt;

    private RecoveryCode(UUID id, UserId userId, String codeHash, UtcInstant usedAt, UtcInstant issuedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("回復コードのハッシュがない");
        }
        this.codeHash = codeHash;
        this.usedAt = usedAt;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    /** 平文の回復コードを発行する。ハッシュだけを持つ。 */
    public static RecoveryCode issue(UserId userId, String code, RecoveryCodeHasher hasher, UtcInstant issuedAt) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("回復コードがない");
        }
        return new RecoveryCode(UUID.randomUUID(), userId, hasher.hash(code), null, issuedAt);
    }

    /** 保存されている状態から回復コードを組み立てる（リポジトリが使う）。使った時刻は、未使用なら null。 */
    public static RecoveryCode reconstitute(
            UUID id, UserId userId, String codeHash, UtcInstant usedAt, UtcInstant issuedAt) {
        return new RecoveryCode(id, userId, codeHash, usedAt, issuedAt);
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String codeHash() {
        return codeHash;
    }

    public UtcInstant issuedAt() {
        return issuedAt;
    }

    public Optional<UtcInstant> usedAt() {
        return Optional.ofNullable(usedAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** 未使用で、平文がハッシュに一致するか。使ったコードは一致しない。 */
    public boolean matches(String code, RecoveryCodeHasher hasher) {
        return !isUsed() && code != null && hasher.matches(code, codeHash);
    }

    /** 使った回復コードを返す。使ったコードはもう一度使えない。 */
    public RecoveryCode use(UtcInstant at) {
        if (isUsed()) {
            throw new IllegalStateException("使った回復コードはもう一度使えない");
        }
        return new RecoveryCode(id, userId, codeHash, Objects.requireNonNull(at, "at"), issuedAt);
    }

    @Override
    public String toString() {
        return "RecoveryCode[id=" + id + ", userId=" + userId + ", used=" + isUsed() + "]";
    }
}
