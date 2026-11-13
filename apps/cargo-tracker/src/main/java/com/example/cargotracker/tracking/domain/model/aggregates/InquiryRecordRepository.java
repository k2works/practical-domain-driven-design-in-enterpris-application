package com.example.cargotracker.tracking.domain.model.aggregates;

/**
 * 照会記録のリポジトリ（送信ポート。Bolt 40-15）。呼び手のトランザクションの中で動く。
 */
@FunctionalInterface
public interface InquiryRecordRepository {

    /** 照会記録を保存する。 */
    void save(InquiryRecord inquiryRecord);
}
