package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 航海・寄港の表（読み書き。Bolt 39e）と接続時間規則の表（読み取りだけ。Bolt 17）の MyBatis マッパー。
 * SQL は同じパッケージの RoutingReferenceMapper.xml に置き、H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 * 航海の読みは航海と寄港の JOIN の 1 文で、航海番号と寄港の順に返す（Bolt 39e の決定 (10)）。
 */
@Mapper
public interface RoutingReferenceMapper {

    /** 判定時刻より後に出発する寄港を持つ航海（寄港の行はすべて）。 */
    List<VoyagePortCallRow> selectCandidateVoyages(@Param("departingAfter") OffsetDateTime departingAfter);

    List<VoyagePortCallRow> selectVoyagesByNumbers(@Param("voyageNumbers") Collection<String> voyageNumbers);

    void insertVoyage(VoyageRow row);

    /** 読み込んだときの版（{@code row.version}）の行だけを更新する（楽観ロック）。0 なら競合。 */
    int updateVoyage(VoyageRow row);

    void deletePortCalls(String voyageNumber);

    void insertPortCall(PortCallRow row);

    List<ConnectionRuleRow> selectConnectionRules();
}
