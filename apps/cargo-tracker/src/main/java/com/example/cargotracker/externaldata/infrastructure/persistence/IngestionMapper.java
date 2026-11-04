package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 取込の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの IngestionMapper.xml に置く。取込の表は追記専用なので、追加と ID で引くこと
 * （Bolt 39c）だけを持つ。
 */
@Mapper
public interface IngestionMapper {

    void insert(IngestionRow row);

    Optional<IngestionRow> selectById(UUID id);
}
