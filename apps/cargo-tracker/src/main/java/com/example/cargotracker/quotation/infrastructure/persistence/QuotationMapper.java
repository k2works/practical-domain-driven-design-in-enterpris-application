package com.example.cargotracker.quotation.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 見積りの MyBatis マッパー。SQL は同じパッケージの QuotationMapper.xml に置く。
 */
@Mapper
public interface QuotationMapper {

    void insertQuotation(QuotationRow row);

    void insertPricingLine(PricingLineRow row);

    /** 楽観ロックで見積りの状態と値を更新し、集約の版を 1 進める。更新した行の数を返す（競合なら 0）。 */
    int updateQuotation(@Param("row") QuotationRow row, @Param("expectedVersion") long expectedVersion);

    /** 輸送要求の見積りを、見積り番号の順に返す。 */
    List<QuotationRow> selectByTransportRequestId(UUID transportRequestId);

    /** 輸送要求の見積りの料金明細を、見積り ID と明細の番号の順に返す（見積りごとに読まない）。 */
    List<PricingLineRow> selectPricingLinesByTransportRequestId(UUID transportRequestId);
}
