package com.example.cargotracker.quotation.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * 輸送要求の MyBatis マッパー。SQL は同じパッケージの TransportRequestMapper.xml に置く。
 */
@Mapper
public interface TransportRequestMapper {

    void insertTransportRequest(TransportRequestRow row);

    void insertTransportRequestVersion(TransportRequestRow row);

    Optional<TransportRequestRow> selectById(UUID id);

    Optional<TransportRequestRow> selectByNumber(String requestNumber);
}
