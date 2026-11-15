package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 照会記録の表（`tracking.inquiry_record`）の MyBatis のマッパー（Bolt 40-15）。SQL は同じパッケージの InquiryRecordMapper.xml に置く。
 */
@Mapper
public interface InquiryRecordMapper {

    void insert(InquiryRecordRow row);

    /** 同じ利用者・同じ追跡番号の顧客 Web の自己照会の件数（指定の時刻より後。IR-INV-01 のまとめの判定）。 */
    int countWebSelfAfter(
            @Param("userId") UUID userId,
            @Param("trackingNumber") String trackingNumber,
            @Param("after") OffsetDateTime after);

    /** まだ有人対応へ移っていない顧客 Web の自己照会（両端を含む。IR-INV-01 の 24 時間の規則。W9 の Bolt 44）。 */
    List<InquiryRecordRow> selectUnescalatedWebSelf(
            @Param("shipperCompanyId") UUID shipperCompanyId,
            @Param("trackingNumber") String trackingNumber,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** 受付時刻が [from, to) の照会の総数（KPI-02。W9 の Bolt 44-1）。 */
    long countBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 受付時刻が [from, to) の、顧客 Web で完結した照会数（自己照会で有人対応へ移っていない）。 */
    long countSelfServedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 有人対応へ移った印と受付番号を付ける（まだ移っていない記録だけ）。 */
    int escalate(@Param("id") UUID id, @Param("caseNumber") String caseNumber);
}
