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

    /** 見積りを ID で返す（経路の割当て。Bolt 20）。 */
    QuotationRow selectById(UUID id);

    void insertAssignedRouteLeg(AssignedRouteLegRow row);

    /** 見積りの割り当てた区間の数（区間は割当ての後に変えないため、まだないときだけ書く。Bolt 20）。 */
    int countAssignedRouteLegs(UUID quotationId);

    /** 輸送要求の見積りの割り当てた区間を、見積り ID と区間番号の順に返す（Bolt 20）。 */
    List<AssignedRouteLegRow> selectAssignedRouteLegsByTransportRequestId(UUID transportRequestId);

    /** 見積提示済みの見積依頼ごとの最新の見積り（受付一覧 S-02。Bolt 11）。 */
    List<QuotedRequestSummaryRow> selectLatestOfQuotedRequests();

    /** 経路設計中の見積依頼の、詳細設計依頼済みの見積りを依頼時刻の古い順に（受付一覧 S-02。Bolt 12）。 */
    List<RoutingRequestedSummaryRow> selectRoutingRequested();
}
