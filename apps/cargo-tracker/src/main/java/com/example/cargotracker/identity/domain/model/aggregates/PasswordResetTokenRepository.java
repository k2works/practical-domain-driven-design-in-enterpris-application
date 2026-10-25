package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 再設定のトークンのリポジトリ（送信ポート。Bolt 33）。実装は infrastructure に置く。発行は呼び出し側のトランザクションの中で、
 * {@link #lockForIssue} で利用者ごとに直列にしてから行う（同時の 2 つの依頼で 2 通送らない。T-100）。
 */
public interface PasswordResetTokenRepository {

    /** 利用者の行をトランザクションの終わりまで占有する（同じ利用者への発行を直列にする）。利用者がいなければ false。 */
    boolean lockForIssue(UserId userId);

    /** 依頼の ID のトークンがあるか（モジュールのイベントの再配信で 2 通送らない）。 */
    boolean existsByRequestId(UUID requestId);

    /** 利用者のその用途の最後の発行時刻。発行がなければ空（Bolt 38b。繰り返しの依頼の抑えは再設定の発行だけで数える）。 */
    Optional<UtcInstant> findLastIssuedAt(UserId userId, PasswordResetTokenPurpose purpose);

    /** 利用者の未使用で無効にしていないトークンを、すべて無効にする。無効にした件数を返す。 */
    int invalidateUnused(UserId userId, UtcInstant at);

    /** 発行したトークンを記録する。 */
    void add(PasswordResetToken token);

    /** ハッシュでトークンを探す（Bolt 33a。リンクを開いたとき）。 */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** ID でトークンを探す（Bolt 33a。A-06 で session のトークンの ID から）。 */
    Optional<PasswordResetToken> findById(UUID id);

    /**
     * 使えるトークン（使っていない、無効にしていない、期限の前）のときだけ、使った時刻を書く。書けたら true。同じトークンの同時の 2 つは
     * 1 つだけが true になる（Bolt 33a、IA-INV-12）。条件は {@link PasswordResetToken#isUsable} と同じ形にし、契約テストで両方を確かめる。
     */
    boolean markUsed(UUID id, UtcInstant at);
}
