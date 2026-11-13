package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.AnswerRow;
import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.ProgressRow;
import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.SourceRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 有人案件の 4 表（`tracking.service_case`・`service_case_source`・`service_case_progress`・`service_case_answer`）の MyBatis のマッパー（Bolt 40-6）。
 * SQL は同じパッケージの ServiceCaseMapper.xml に置き、H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 */
@Mapper
public interface ServiceCaseMapper {

    void insertCase(ServiceCaseRow row);

    /** 版が一致する行だけ更新して版を 1 増やす。更新した行の数（一致しなければ 0）を返す。 */
    int updateCase(ServiceCaseRow row);

    Optional<ServiceCaseRow> selectCase(@Param("caseNumber") String caseNumber);

    void insertSource(SourceRow row);

    void insertProgress(ProgressRow row);

    void insertAnswer(AnswerRow row);

    /** 顧客の回答を書く。まだ書かれていない回答だけ（回答は書き換えない）。 */
    int updateCustomerResponse(
            @Param("caseNumber") String caseNumber,
            @Param("answerNo") int answerNo,
            @Param("customerResponse") String customerResponse,
            @Param("customerRespondedAt") OffsetDateTime customerRespondedAt);

    List<SourceRow> selectSources(@Param("caseNumber") String caseNumber);

    List<ProgressRow> selectProgress(@Param("caseNumber") String caseNumber);

    List<AnswerRow> selectAnswers(@Param("caseNumber") String caseNumber);

    int countProgress(@Param("caseNumber") String caseNumber);

    int countAnswers(@Param("caseNumber") String caseNumber);

    /** 受領期限を過ぎた案件（受付済みで期限を厳密に過ぎたものと、上位対応中）の受付番号を期限の古い順に。 */
    List<String> selectReceiptOverdue(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /** 指定した状態の案件の受付番号を受領期限の古い順に（Bolt 40-11）。 */
    List<String> selectByStatuses(@Param("statuses") List<String> statuses, @Param("limit") int limit);

    /** 起票元キーで起票済みの案件の受付番号（Bolt 40-17）。 */
    Optional<String> selectByOriginRef(@Param("originRef") String originRef);

    /** 起票元キーを記録する（一意。Bolt 40-17）。 */
    int updateOriginRef(@Param("caseNumber") String caseNumber, @Param("originRef") String originRef);

    /** 同じ荷主企業・予約（なければ「ない」案件）・内容で未終結の案件の受付番号（古い順の先頭）。 */
    Optional<String> selectUnclosedDuplicate(
            @Param("shipperCompanyId") java.util.UUID shipperCompanyId,
            @Param("bookingId") java.util.UUID bookingId,
            @Param("cause") String cause);
}
