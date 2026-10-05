package com.example.cargotracker.quotation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 輸送要求の MyBatis マッパー。SQL は同じパッケージの TransportRequestMapper.xml に置く。
 */
@Mapper
public interface TransportRequestMapper {

    void insertTransportRequest(TransportRequestRow row);

    void insertTransportRequestVersion(TransportRequestRow row);

    /** 楽観ロックで状態と現在の版番号を更新し、集約の版を 1 進める。更新した行の数を返す（競合なら 0）。 */
    int updateTransportRequest(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("currentVersionNo") int currentVersionNo,
            @Param("expectedVersion") long expectedVersion);

    /** 審査記録がまだなければ追加する（追記専用）。 */
    void insertReviewRecordIfAbsent(ReviewRecordRow row);

    List<ReviewRecordRow> selectReviewRecords(UUID transportRequestId);

    /** 新しい版の必要書類を追加する（追記専用。出し直しでは引き継いだ書類も新しい版の行として足す）。 */
    void insertRequiredDocument(RequiredDocumentRow row);

    /** 版の必要書類を書類番号の順に読む。 */
    List<RequiredDocumentRow> selectRequiredDocuments(
            @Param("transportRequestId") UUID transportRequestId, @Param("versionNo") int versionNo);

    Optional<TransportRequestRow> selectByNumberForStaff(String requestNumber);

    List<TransportRequestSummaryRow> selectUnderReviewSummaries();

    List<TransportRequestSummaryRow> selectQuotingSummaries();

    List<TransportRequestSummaryRow> selectSummariesByShipper(UUID shipperCompanyId);

    Optional<TransportRequestRow> selectById(UUID id);

    Optional<TransportRequestRow> selectByNumber(
            @Param("requestNumber") String requestNumber, @Param("shipperCompanyId") UUID shipperCompanyId);
}
