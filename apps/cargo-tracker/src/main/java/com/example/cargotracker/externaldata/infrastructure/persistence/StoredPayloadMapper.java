package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 保存した原本の MyBatis マッパー（Bolt 39a2）。SQL は同じパッケージの StoredPayloadMapper.xml に置く。
 */
@Mapper
public interface StoredPayloadMapper {

    void insert(
            @Param("objectKey") String objectKey,
            @Param("content") byte[] content,
            @Param("storedAt") OffsetDateTime storedAt);

    Optional<byte[]> selectContent(String objectKey);
}
