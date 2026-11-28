package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.UUID;

/**
 * 同じ受信記録の判断がもうある（同時の判断・二重の送信で先に書かれた。受信記録 ID の一意制約）。判断のリポジトリのポートの約束で、技術の
 * 例外（Spring の {@code DuplicateKeyException}）を名指ししないための名前付きの例外（Bolt 39d2 の行 6。前例
 * {@link DuplicateAdoptedValueException}）。却下のサービスは判断済みとして扱う。
 */
public final class DuplicateReceivedRecordDecisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private DuplicateReceivedRecordDecisionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 受信記録の判断の重複。
     *
     * @param receivedRecordId 受信記録 ID
     * @param cause 元の例外（メモリの実装は null）
     * @return 例外
     */
    public static DuplicateReceivedRecordDecisionException of(UUID receivedRecordId, Throwable cause) {
        return new DuplicateReceivedRecordDecisionException("受信記録の判断がもうある: " + receivedRecordId, cause);
    }
}
