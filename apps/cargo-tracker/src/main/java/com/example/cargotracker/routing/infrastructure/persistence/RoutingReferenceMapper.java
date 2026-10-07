package com.example.cargotracker.routing.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 航海・寄港・接続時間規則の表の MyBatis マッパー（読み取りだけ。Bolt 17）。
 * SQL は同じパッケージの RoutingReferenceMapper.xml に置き、H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 */
@Mapper
public interface RoutingReferenceMapper {

    List<VoyageRow> selectVoyages();

    /** 寄港を航海番号と寄港の順に返す。 */
    List<PortCallRow> selectPortCalls();

    List<ConnectionRuleRow> selectConnectionRules();
}
