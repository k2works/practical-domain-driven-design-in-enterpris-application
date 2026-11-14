package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
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
}
