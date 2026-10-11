package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * ログインの失敗のリポジトリ（送信ポート）。実装は infrastructure に置く。
 * 失敗の加算とロックの判定は、利用者の版に触れず、1 文の条件付きの更新で原子的に行う（IA-INV-03、ADR-011）。
 */
public interface LoginFailureRepository {

    /** 利用者のログインの失敗を返す。記録のない利用者は「0 回・ロックなし」。 */
    LoginFailure findByUserId(UserId userId);

    /**
     * その時刻の失敗を数え、数えた後のログインの失敗を返す。規則は {@link LoginFailure#recordFailure} と同じで、
     * ロック中は数えず、ロックが解けた後は 1 回目から数え、上限に達したらロックする。同時の失敗は両方を数える。
     * 存在しない利用者の失敗では記録を作らず、「0 回・ロックなし」を返す。
     * 同じ利用者の最初の失敗が同時に 2 つ届くと、片方は記録の作成で一意制約に当たる（Bolt 32 で扱う）。
     */
    LoginFailure recordFailure(UserId userId, UtcInstant failedAt, LockPolicy policy);

    /** 成功を記録する。ロック中でなければ回数を 0 に戻しロックを外す。記録のない利用者では何もしない。 */
    void recordSuccess(UserId userId, UtcInstant succeededAt);
}
