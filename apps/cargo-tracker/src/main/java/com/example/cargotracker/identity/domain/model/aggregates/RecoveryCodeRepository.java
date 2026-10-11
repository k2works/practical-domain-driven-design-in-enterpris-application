package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * 回復コードのリポジトリ（送信ポート）。実装は infrastructure に置く。
 * 消費は、読んでから書く形にせず、1 文の条件付きの更新で原子的に判定する（IA-INV-11、ADR-011）。
 */
public interface RecoveryCodeRepository {

    /**
     * 利用者の回復コードを、発行した 10 個に置き換える（使ったコードも消す）。呼び出し側のトランザクションの中で使う。
     */
    void replaceAll(UserId userId, List<RecoveryCode> codes);

    /** 利用者の未使用の回復コードを、発行した順に返す。 */
    List<RecoveryCode> findUnusedByUserId(UserId userId);

    /**
     * 未使用のときだけ、使った時刻を書く。書けたら true。同じコードの同時の消費は 1 つだけが true になる。
     * 存在しないコードは false（失敗へ倒す。T-42）。
     */
    boolean use(UUID recoveryCodeId, UtcInstant usedAt);
}
