package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 受信記録のリポジトリ。受信記録を追記し、取込と分類ごとに数え、取込の隔離を行の範囲の順に読むドメインのポート（Bolt 39b。読む照会は
 * Bolt 39c3。判断を待つ隔離の一覧の照会は Bolt 39d。ID で引く照会は Bolt 39d2）。受信記録の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため）。呼び手は原本の保存に成功してから書く。
 */
public interface ReceivedRecordRepository {

    /** 受信記録を追記する。 */
    void add(ReceivedRecord receivedRecord);

    /** その取込の、その分類の受信記録の件数。 */
    int countByIngestionAndClassification(UUID ingestionId, Classification classification);

    /**
     * その取込の隔離と差異の受信記録を、行の範囲の始まりの順（範囲のないファイルの誤りを先、同じなら受信記録 ID の順）に最大 {@code limit} 件
     * （S-16 の結果の画面の隔離・差異の理由。Bolt 39c3。差異は Bolt 39h。名前は Bolt 39h2 で隔離だけでなく差異も返すことに合わせた）。
     */
    List<ReceivedRecord> quarantinesAndDiscrepanciesOf(UUID ingestionId, int limit);

    /**
     * 判断を待つ隔離・差異（分類が隔離か差異で、受信記録の判断の行がない、取込で書いた受信記録。差異は Bolt 39h2）を、取込の新しい順、同じ時刻の取込は取込 ID の順、同じ取込の
     * 中は範囲のないファイルの誤りを先に行の範囲の始まりの順、受信記録 ID の順に、{@code offset} 件を飛ばして最大 {@code limit} 件（S-17 の
     * 一覧。Bolt 39d）。取込の時刻・情報源 ID と外部原本の外部 ID・外部版を結んで読む。
     *
     * @param ingestionIdOrNull 絞る取込 ID（{@code null} なら絞らない）
     */
    List<UndecidedRecord> undecidedRecords(UUID ingestionIdOrNull, int offset, int limit);

    /** 判断を待つ隔離・差異の件数（{@link #undecidedRecords} と同じ絞り込み。Bolt 39d）。 */
    int countUndecidedRecords(UUID ingestionIdOrNull);

    /**
     * ID で受信記録を引く（判断できる受信記録の確かめ。Bolt 39d2）。取込で書いた受信記録だけを返し、取込のない（{@code ingestion_id} が NULL の）
     * 受信記録とないときは空（受信記録の record は取込 ID を必ず持つ）。分類では絞らない。
     */
    Optional<ReceivedRecord> findById(UUID id);

    /**
     * ID で絞った判断を待つ隔離（S-17 の判断の画面の中身。{@link #undecidedRecords} と同じ定め。Bolt 39d2）。隔離でない・判断のある・取込の
     * ない受信記録とないときは空。
     */
    Optional<UndecidedRecord> undecidedRecord(UUID receivedRecordId);

    /**
     * その取込の判断を待つキーのない隔離（分類が隔離で、外部原本を持たず、受信記録の判断の行がない受信記録）を、行の範囲の始まりの順（範囲の
     * ないファイルの誤りを先、同じなら受信記録 ID の順。{@link #quarantinesAndDiscrepanciesOf} と同じ並び）に、件数の上限なしで（まとめての却下の対象。
     * Bolt 39d2b）。1 つの取込のキーのない隔離はデータ行の上限 10,000 件と取り込めなかった部分・ファイルの誤りの数件に収まる。
     */
    List<ReceivedRecord> undecidedKeylessQuarantinesOf(UUID ingestionId);

    /** その取込の判断を待つキーのない隔離の件数（{@link #undecidedKeylessQuarantinesOf} と同じ定め。Bolt 39d2b）。 */
    int countUndecidedKeylessQuarantines(UUID ingestionId);
}
