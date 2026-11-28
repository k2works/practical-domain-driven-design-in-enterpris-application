package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情報源の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの DataSourceMapper.xml に置く。読み取りだけを持つ。
 */
@Mapper
public interface DataSourceMapper {

    Optional<DataSourceRow> selectById(UUID id);

    List<DataSourceRow> selectApproved();

    int countApproved();
}
