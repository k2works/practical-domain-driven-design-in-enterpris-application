package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

/**
 * 照会記録のリポジトリ（送信ポート。Bolt 40-15）。呼び手のトランザクションの中で動く。
 */
public interface InquiryRecordRepository {

    /** 照会記録を保存する。 */
    void save(InquiryRecord inquiryRecord);

    /**
     * 同じ利用者が同じ追跡番号を顧客 Web で照会した記録が、指定の時刻より後にあるか（IR-INV-01 の 30 分のまとめの判定。Bolt 42-1）。
     *
     * @param after この時刻より後（この時刻は含まない）
     */
    boolean existsWebSelfAfter(UserId inquirerUserId, TrackingNumber trackingNumber, UtcInstant after);
}
