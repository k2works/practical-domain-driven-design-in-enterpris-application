package com.example.cargotracker.externaldata.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;

/**
 * 取込の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの IngestionMapper.xml に置く。取込の表は追記専用なので、追加だけを持つ。
 */
@Mapper
public interface IngestionMapper {

    void insert(IngestionRow row);
}
