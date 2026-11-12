package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 採用値の MyBatis マッパー（Bolt 39b3）。SQL は同じパッケージの AdoptedValueMapper.xml に置く。採用値の表は追記専用なので、追加と
 * 受信記録 ID での照会と、外部原本のこの外部版の採用値の照会（Bolt 39d3）と、この外部 ID の採用値の照会（Bolt 39h）だけを持つ。
 */
@Mapper
public interface AdoptedValueMapper {

    Optional<AdoptedValueRow> selectByReceivedRecordId(UUID receivedRecordId);

    /** この外部版の採用値（受信記録を結んで外部原本 ID で絞り、公開した時刻の新しい順、採用値 ID の順の最後の 1 件。Bolt 39d3）。 */
    Optional<AdoptedValueRow> selectCurrentOf(UUID externalRecordId);

    /** この外部 ID の採用値（外部原本の冪等性キーの先頭 3 列で絞り、受信記録と採用値を結ぶ。並びは決めない。Bolt 39h）。 */
    List<ExternalIdAdoptedValueRow> selectByExternalId(
            @Param("provider") String provider,
            @Param("recordType") String recordType,
            @Param("externalId") String externalId);

    void insert(AdoptedValueRow row);

    /** この外部 ID の採用値の照会の 1 行（採用値に受信記録の取得時刻を結んだ行。Bolt 39h）。 */
    record ExternalIdAdoptedValueRow(
            String infoVersion,
            String targetKey,
            OffsetDateTime acquiredAt,
            OffsetDateTime publishedAt,
            UUID receivedRecordId) {}
}
