package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.UUID;

/**
 * 外部原本の基準を読んだ後に、ほかの採用が先に基準を付け替えていた（読んだハッシュを条件にした更新の件数が 0。外部原本は版の列を持たない。Bolt 39d4 の開始準備の
 * 決定 (4)）。外部原本のリポジトリのポートの約束で、判断のサービスは捕まえずに上げてトランザクションごと巻き戻し（判断・採用値・イベントの発行記録を残さない）、判断の
 * 画面の入口が捕まえて判断の画面へ戻す。
 */
public final class ConcurrentExternalRecordUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private ConcurrentExternalRecordUpdateException(String message) {
        super(message);
    }

    /**
     * 外部原本の基準の競り合い。
     *
     * @param externalRecordId 外部原本 ID
     * @return 例外
     */
    public static ConcurrentExternalRecordUpdateException of(UUID externalRecordId) {
        return new ConcurrentExternalRecordUpdateException(
                "外部原本 " + externalRecordId + " の基準は読んだ後に付け替えられたか、外部原本がありません");
    }
}
