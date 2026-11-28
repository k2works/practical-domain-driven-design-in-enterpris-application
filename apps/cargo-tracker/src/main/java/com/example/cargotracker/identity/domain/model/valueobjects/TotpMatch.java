package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * TOTP の照合の結果。入れたコードが、使っていない刻みのコードと一致したか、使った刻み以下のコードとだけ一致したか（同じ
 * コードの再利用）、どれとも一致しなかったかを表す（Bolt 31d、ADR-011 の決定 8、IA-INV-10）。拒否の種類は監査記録の理由に
 * だけ使い、利用者には同じ応答を返す（AC2）。
 */
@ValueObject
public sealed interface TotpMatch {

    /**
     * 使っていない刻みのコードと一致した。
     *
     * @param step 一致した刻み（リポジトリの条件付きの更新で使ったことにする）
     */
    record Matched(long step) implements TotpMatch {}

    /** 窓の中の、使った刻み以下のコードとだけ一致した（同じコードの再利用）。 */
    record Reused() implements TotpMatch {}

    /** 窓の中のどの刻みのコードとも一致しなかった（形の違うコードを含む）。 */
    record Mismatch() implements TotpMatch {}
}
