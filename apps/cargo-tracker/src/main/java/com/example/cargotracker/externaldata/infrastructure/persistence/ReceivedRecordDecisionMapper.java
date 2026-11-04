package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 受信記録の判断の MyBatis マッパー（Bolt 39d2）。SQL は同じパッケージの ReceivedRecordDecisionMapper.xml に置く。判断の表は追記専用なので、
 * 追加と受信記録ごとの件数だけを持つ。
 */
@Mapper
public interface ReceivedRecordDecisionMapper {

    void insert(ReceivedRecordDecisionRow row);

    int countByReceivedRecordId(UUID receivedRecordId);
}
