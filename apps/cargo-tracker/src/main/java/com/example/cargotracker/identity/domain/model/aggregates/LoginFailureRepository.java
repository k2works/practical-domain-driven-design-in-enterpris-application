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
     * 利用者の 0 回の記録を、なければ作る（Bolt 32）。存在しない利用者では作らない。同じ利用者の最初の失敗が同時に届いても
     * 1 行だけになる（作成の一意制約の違反は、既にあるとして受ける）。違反を受けるので、トランザクションの外で呼ぶ（ADR-007 の
     * 「一意制約の違反は同じトランザクションで捕まえない」。Bolt 30 の議題 2 の③）。
     */
    void prepare(UserId userId, UtcInstant at);

    /**
     * その時刻の失敗を数え、数えたかと数えた後のログインの失敗を返す（記録は {@link #prepare} で先に作っておく）。規則は
     * {@link LoginFailure#recordFailure} と同じで、ロック中は数えず、ロックが解けた後は 1 回目から数え、上限に達したらロックする。
     * 同時の失敗は両方を数える。記録のない利用者（存在しない利用者を含む）では数えず、「0 回・ロックなし」を返す。
     */
    FailureCount recordFailure(UserId userId, UtcInstant failedAt, LockPolicy policy);

    /** 成功を記録する。ロック中でなければ回数を 0 に戻しロックを外す。記録のない利用者では何もしない。 */
    void recordSuccess(UserId userId, UtcInstant succeededAt);

    /**
     * システム管理者の解除（Bolt 38c）。その時刻にロック中のときだけ回数を 0 に戻しロックを外し、外したら true。規則は
     * {@link LoginFailure#unlock} と同じ。期限の切れたロック・失敗の途中・記録のない利用者は書かずに false（同時の 2 つの解除は
     * 1 つだけが true になる）。
     */
    boolean unlock(UserId userId, UtcInstant now);
}
