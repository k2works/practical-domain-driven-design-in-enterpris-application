package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.util.Optional;
import java.util.UUID;

/**
 * 外部原本のリポジトリ。外部原本を冪等性キーで引き、足すドメインのポート（Bolt 39b）。同じトランザクションで先に足した外部原本も引ける
 * （同じファイルの中の同じキーを行の順に分類するため）。
 */
public interface ExternalRecordRepository {

    /** 冪等性キー（提供元・原本種別・外部 ID・外部版の 4 つ）で外部原本を引く。ないときは空。 */
    Optional<ExternalRecord> findByKey(IdempotencyKey key);

    /** 外部原本を ID で引く。ないときは空（Bolt 39d3b。採用の判断が受信記録の外部原本 ID から冪等性キーを読む）。 */
    Optional<ExternalRecord> findById(UUID externalRecordId);

    /**
     * 外部原本を足す。
     *
     * <p>同じ冪等性キーの外部原本がもうあれば（同時の取込）、{@link DuplicateExternalRecordException} を投げる。取込のサービスは
     * 捕まえずに上げ、取込全体を巻き戻す（Bolt 39b の確認ポイント 7、Bolt 39b3 の行 12）。
     *
     * @throws DuplicateExternalRecordException 同じ冪等性キーの外部原本がもうあるとき
     */
    void add(ExternalRecord externalRecord);

    /**
     * 外部原本の基準を採用した payload に付け替える（Bolt 39d4 の開始準備の決定 (3)・(4)、【業務の判断】(b)）。読んだハッシュを条件にした更新で、冪等性キーと最初の受信時刻は
     * 変えない。条件のハッシュが今の基準と違う（読んだ後にほかの採用が先に付け替えた）か、外部原本がないときは例外を投げる。
     *
     * @param externalRecordId 外部原本 ID
     * @param expectedHash 読んだ基準のハッシュ
     * @param adoptedHash 採用した payload のハッシュ
     * @param adoptedObjectKey 採用した payload のオブジェクトキー
     * @throws ConcurrentExternalRecordUpdateException 条件のハッシュが今の基準と違う、または外部原本がないとき
     */
    void changeAdoptedPayload(
            UUID externalRecordId,
            PayloadHash expectedHash,
            PayloadHash adoptedHash,
            PayloadObjectKey adoptedObjectKey);
}
