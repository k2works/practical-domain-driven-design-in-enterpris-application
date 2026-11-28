package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.UUID;

/**
 * 同じ受信記録の採用値がもうある（受信記録ごとに 1 つ。採用値の受信記録 ID の一意制約）。採用値のリポジトリのポートの約束で、技術の
 * 例外（Spring の {@code DuplicateKeyException}）を名指ししないための名前付きの例外（Bolt 39b3 の行 15。前例は
 * {@link DuplicateExternalRecordException}）。取込のサービスは捕まえずに上げ、取込全体を巻き戻す。
 */
public final class DuplicateAdoptedValueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private DuplicateAdoptedValueException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 受信記録の採用値の重複。
     *
     * @param receivedRecordId 受信記録 ID
     * @param cause 元の例外（メモリの実装は null）
     * @return 例外
     */
    public static DuplicateAdoptedValueException of(UUID receivedRecordId, Throwable cause) {
        return new DuplicateAdoptedValueException("受信記録の採用値がもうある: " + receivedRecordId, cause);
    }
}
