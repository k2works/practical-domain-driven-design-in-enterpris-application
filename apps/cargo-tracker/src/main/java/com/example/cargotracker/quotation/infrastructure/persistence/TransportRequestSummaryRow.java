package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 輸送要求の一覧の 1 行（輸送要求のヘッダ、現在の版、版 1 の提出時刻を結合した射影）。
 */
public record TransportRequestSummaryRow(
        String requestNumber,
        int currentVersionNo,
        String status,
        OffsetDateTime firstSubmittedAt,
        OffsetDateTime currentSubmittedAt,
        String originUnlocode,
        String destinationUnlocode,
        OffsetDateTime arrivalDeadline,
        String cargoCategory) {}
