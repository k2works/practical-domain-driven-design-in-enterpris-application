package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import java.util.Optional;

/**
 * 外部原本のリポジトリ。外部原本を冪等性キーで引き、足すドメインのポート（Bolt 39b）。同じトランザクションで先に足した外部原本も引ける
 * （同じファイルの中の同じキーを行の順に分類するため）。
 */
public interface ExternalRecordRepository {

    /** 冪等性キー（提供元・原本種別・外部 ID・外部版の 4 つ）で外部原本を引く。ないときは空。 */
    Optional<ExternalRecord> findByKey(IdempotencyKey key);

    /**
     * 外部原本を足す。
     *
     * <p>同じ冪等性キーの外部原本がもうあれば（同時の取込）、{@link DuplicateExternalRecordException} を投げる。取込のサービスは
     * 捕まえずに上げ、取込全体を巻き戻す（Bolt 39b の確認ポイント 7、Bolt 39b3 の行 12）。
     *
     * @throws DuplicateExternalRecordException 同じ冪等性キーの外部原本がもうあるとき
     */
    void add(ExternalRecord externalRecord);
}
