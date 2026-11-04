package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受信記録の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの ReceivedRecordMapper.xml に置く。受信記録の表は追記専用なので、
 * 追加と数えることと、取込の隔離を行の範囲の順に読むこと（Bolt 39c3）だけを持つ。
 */
@Mapper
public interface ReceivedRecordMapper {

    void insert(ReceivedRecordRow row);

    int countByIngestionAndClassification(
            @Param("ingestionId") UUID ingestionId, @Param("classification") String classification);

    List<ReceivedRecordRow> quarantinedOf(@Param("ingestionId") UUID ingestionId, @Param("limit") int limit);
}
