package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 有人案件の子の表の行（Bolt 40-6）。出典・進捗・回答。
 */
public final class ServiceCaseChildRows {

    private ServiceCaseChildRows() {}

    /** `service_case_source` の 1 行。 */
    public record SourceRow(
            String caseNumber, int sourceNo, String sourceKind, String sourceRef, OffsetDateTime acquiredAt) {}

    /** `service_case_progress` の 1 行。 */
    public record ProgressRow(
            String caseNumber,
            int progressNo,
            String content,
            OffsetDateTime nextUpdateAt,
            UUID updatedBy,
            OffsetDateTime updatedAt) {}

    /** `service_case_answer` の 1 行。 */
    public record AnswerRow(
            String caseNumber,
            int answerNo,
            String content,
            UUID answeredBy,
            OffsetDateTime answeredAt,
            String customerResponse,
            OffsetDateTime customerRespondedAt) {}
}
