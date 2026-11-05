package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 採用値の MyBatis マッパー（Bolt 39b3）。SQL は同じパッケージの AdoptedValueMapper.xml に置く。採用値の表は追記専用なので、追加と
 * 受信記録 ID での照会と、外部原本のこの外部版の採用値の照会（Bolt 39d3）だけを持つ。
 */
@Mapper
public interface AdoptedValueMapper {

    Optional<AdoptedValueRow> selectByReceivedRecordId(UUID receivedRecordId);

    /** この外部版の採用値（受信記録を結んで外部原本 ID で絞り、公開した時刻の新しい順、採用値 ID の順の最後の 1 件。Bolt 39d3）。 */
    Optional<AdoptedValueRow> selectCurrentOf(UUID externalRecordId);

    void insert(AdoptedValueRow row);
}
