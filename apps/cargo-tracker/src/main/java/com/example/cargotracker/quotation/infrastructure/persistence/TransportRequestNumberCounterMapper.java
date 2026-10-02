package com.example.cargotracker.quotation.infrastructure.persistence;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/**
 * 業務番号の採番の表（quotation.transport_request_number_counter）の MyBatis マッパー。
 * SQL は同じパッケージの TransportRequestNumberCounterMapper.xml に置き、H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 */
@Mapper
public interface TransportRequestNumberCounterMapper {

    /** その年の行を作る（last_no = 0）。行が既にあれば一意制約の違反になる。 */
    void insertYear(int year);

    /** その年の last_no を 1 増やし、行をロックする。更新した行の数（行がなければ 0）を返す。 */
    int increment(int year);

    /** その年の last_no を読む。 */
    Optional<Integer> selectLastNo(int year);
}
