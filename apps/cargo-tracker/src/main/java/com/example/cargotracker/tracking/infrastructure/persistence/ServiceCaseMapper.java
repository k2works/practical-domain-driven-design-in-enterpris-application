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
}
