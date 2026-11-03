package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 採用値の MyBatis マッパー（Bolt 39b3）。SQL は同じパッケージの AdoptedValueMapper.xml に置く。採用値の表は追記専用なので、追加と
 * 受信記録 ID での照会だけを持つ。
 */
@Mapper
public interface AdoptedValueMapper {

    Optional<AdoptedValueRow> selectByReceivedRecordId(UUID receivedRecordId);

    void insert(AdoptedValueRow row);
}
