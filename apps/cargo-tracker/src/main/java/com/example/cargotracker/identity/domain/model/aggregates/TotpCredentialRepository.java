package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;

/**
 * TOTP の資格のリポジトリ（送信ポート）。実装は infrastructure に置く。
 * 刻みの使用は、読んでから書く形にせず、1 文の条件付きの更新で原子的に判定する（IA-INV-10、ADR-011）。
 */
public interface TotpCredentialRepository {

    /** 登録した資格を保存する。同じ利用者の資格が既にあれば、表の一意制約で拒否される（再登録は Bolt 33b）。 */
    void add(TotpCredential credential);

    Optional<TotpCredential> findByUserId(UserId userId);

    /**
     * 最後に使った刻みより後の刻みのときだけ、その刻みを最後に使った刻みとして書く。書けたら true。
     * 同じ刻みの同時の使用は 1 つだけが true になる。資格のない利用者は false（失敗へ倒す。T-42）。
     */
    boolean useStep(UserId userId, long step);
}
