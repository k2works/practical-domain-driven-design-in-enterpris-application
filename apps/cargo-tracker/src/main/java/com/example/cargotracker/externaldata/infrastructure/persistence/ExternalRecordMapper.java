package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 外部原本の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの ExternalRecordMapper.xml に置く。 */
@Mapper
public interface ExternalRecordMapper {

    Optional<ExternalRecordRow> selectByKey(
            @Param("provider") String provider,
            @Param("recordType") String recordType,
            @Param("externalId") String externalId,
            @Param("externalVersion") String externalVersion);

    void insert(ExternalRecordRow row);
}
