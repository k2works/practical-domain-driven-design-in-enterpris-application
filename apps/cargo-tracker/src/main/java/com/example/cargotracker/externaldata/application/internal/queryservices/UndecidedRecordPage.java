package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 判断を待つ隔離・差異の 1 ページ。判断を待つ隔離・差異の照会の値（S-17 の一覧。Bolt 39d）。1 ページは
 * {@link UndecidedRecordQueryService#PAGE_SIZE} 件で、示す頁は 1 から頁の数まで（件数が 0 なら 1 で、頁の数は 0）。
 *
 * @param items 示す判断を待つ隔離（取込の新しい順・ファイルの行の順。並びは受信記録の照会が決める）
 * @param total 判断を待つ隔離の件数（絞り込みの中の全件）
 * @param page 示す頁（1 から）
 * @param pageCount 頁の数
 * @param ingestionFilter 絞った取込 ID（絞らないとき、絞った取込が見つからないときは {@code null}）
 * @param ingestionNotFound 絞った取込が見つからず、絞らない一覧を返したか
 * @param keylessQuarantineCount 絞った取込の判断を待つキーのない隔離の件数（まとめての却下のリンクの件数。取込で絞ったときだけ数え、
 *     絞らないとき・見つからないとき・判断を待つ隔離が 0 件のときは 0。Bolt 39d2b）
 */
public record UndecidedRecordPage(
        List<Item> items,
        int total,
        int page,
        int pageCount,
        UUID ingestionFilter,
        boolean ingestionNotFound,
        int keylessQuarantineCount) {

    public UndecidedRecordPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /** 示す最初の件の番号（1 から。件数が 0 なら 0）。 */
    public int firstShown() {
        return items.isEmpty() ? 0 : (page - 1) * UndecidedRecordQueryService.PAGE_SIZE + 1;
    }

    /** 示す最後の件の番号（件数が 0 なら 0）。 */
    public int lastShown() {
        return items.isEmpty() ? 0 : firstShown() + items.size() - 1;
    }

    /** 前の頁があるか。 */
    public boolean hasPrevious() {
        return page > 1;
    }

    /** 次の頁があるか。 */
    public boolean hasNext() {
        return page < pageCount;
    }

    /**
     * 判断を待つ隔離・差異の 1 件。
     *
     * @param receivedRecordId 受信記録 ID
     * @param ingestedAt 取り込んだ日時（取込の取込時刻）
     * @param provider 情報源の提供元
     * @param recordType 情報源の原本種別（取込ファイルの種別の名前）
     * @param lineRange 行の範囲（ファイルの誤りは {@code null}）
     * @param classification 分類（隔離か差異。Bolt 39h2）。外部原本を持たない隔離がキーのない隔離（形式の誤り）で、持てば内容の違う原本か差異
     * @param externalId 外部 ID（キーのない隔離は {@code null}）
     * @param externalVersion 外部版（キーのない隔離は {@code null}）
     * @param note 隔離の理由（分類の注記）
     */
    public record Item(
            UUID receivedRecordId,
            Instant ingestedAt,
            String provider,
            String recordType,
            PhysicalLineRange lineRange,
            Classification classification,
            String externalId,
            String externalVersion,
            String note) {

        /** キーのない隔離（形式の誤り）か（外部原本を持たない）。 */
        public boolean keyless() {
            return externalId == null;
        }
    }
}
