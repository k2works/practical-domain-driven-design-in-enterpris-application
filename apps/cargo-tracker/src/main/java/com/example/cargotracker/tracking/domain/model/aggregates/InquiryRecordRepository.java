package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.UUID;

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

    /**
     * 有人対応へまだ移っていない、顧客 Web の自己照会（WEB_SELF）の記録を、荷主企業と追跡番号と受付時刻の範囲で探す（IR-INV-01 の 24 時間の
     * 規則。W9 の Bolt 44）。両端を含む。
     *
     * @param from この時刻以後
     * @param to この時刻以前
     */
    List<InquiryRecord> findUnescalatedWebSelf(
            CompanyId shipperCompanyId, TrackingNumber trackingNumber, UtcInstant from, UtcInstant to);

    /**
     * 受付時刻が範囲（始まりを含み、終わりを含まない）にある照会の件数（KPI-02。W9 の Bolt 44-1）。
     *
     * @param from この時刻以後
     * @param to この時刻より前
     */
    InquiryCountSummary countBetween(UtcInstant from, UtcInstant to);

    /** 照会記録を、引き継いだ有人案件の受付番号つきにする（有人対応へ移った。まだ移っていない記録だけを更新する）。 */
    void escalate(UUID inquiryRecordId, CaseNumber caseNumber);
}
