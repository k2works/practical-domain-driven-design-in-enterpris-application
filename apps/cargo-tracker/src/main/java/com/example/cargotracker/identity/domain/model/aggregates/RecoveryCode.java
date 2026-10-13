package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 回復コード。TOTP の端末を失ったときに一度だけ使えるコード。ハッシュだけを持ち、平文を持たない（IA-INV-11、SEC-09）。
 * 自分のリポジトリを持ち、消費を 1 文の条件付きの更新で原子的に行うため、小さな集約とする。使ったコードは
 * 使った時刻を入れて残す。発行のたびに利用者のコードを置き換える（発行の流れは Bolt 31）。
 */
@AggregateRoot
public final class RecoveryCode {

    /** 利用者ごとに発行する数（SEC-09）。 */
    public static final int CODES_PER_USER = 10;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern UNHYPHENATED = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern NORMALIZED = Pattern.compile("[0-9a-f]{4}-[0-9a-f]{4}");

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

    /**
     * 発行した回復コードの組が、その利用者の 10 個であることを確かめる（IA-INV-11、SEC-09）。
     * リポジトリが置き換える前に使う。
     */
    public static List<RecoveryCode> requireIssuedSet(UserId userId, List<RecoveryCode> codes) {
        Objects.requireNonNull(userId, "userId");
        if (codes == null || codes.size() != CODES_PER_USER) {
            throw new IllegalArgumentException("回復コードは " + CODES_PER_USER + " 個で発行する");
        }
        if (codes.stream().anyMatch(code -> !code.userId().equals(userId) || code.isUsed())) {
            throw new IllegalArgumentException("発行する回復コードはその利用者の未使用のコードにする");
        }
        return List.copyOf(codes);
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

    /**
     * 利用者が入力した回復コードを、照合する形（小文字の 16 進 4 文字・ハイフン・4 文字。発行の形と同じ）に直す（Bolt 31c）。
     * 書き写したコードを打ち直させないため、空白・大文字・ハイフンの抜けを受ける。形が合わなければ空にし、照合させない
     * （bcrypt を回さない）。
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String compact = WHITESPACE.matcher(input).replaceAll("").toLowerCase(Locale.ROOT);
        String hyphenated = UNHYPHENATED.matcher(compact).matches()
                ? compact.substring(0, 4) + "-" + compact.substring(4)
                : compact;
        return NORMALIZED.matcher(hyphenated).matches() ? Optional.of(hyphenated) : Optional.empty();
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
