package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;

/**
 * 同じ冪等性キーの外部原本がもうある（同時の取込で先に書かれた。冪等性キーの一意制約）。外部原本のリポジトリのポートの約束で、技術の
 * 例外（Spring の {@code DuplicateKeyException}）を名指ししないための名前付きの例外（Bolt 39b3 の行 12）。取込のサービスは捕まえずに
 * 上げ、取込全体を巻き戻す（Bolt 39b の確認ポイント 7）。
 */
public final class DuplicateExternalRecordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private DuplicateExternalRecordException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 冪等性キーの外部原本の重複。
     *
     * @param key 冪等性キー
     * @param cause 元の例外（メモリの実装は null）
     * @return 例外
     */
    public static DuplicateExternalRecordException of(IdempotencyKey key, Throwable cause) {
        return new DuplicateExternalRecordException("冪等性キーの外部原本がもうある: " + key, cause);
    }
}
