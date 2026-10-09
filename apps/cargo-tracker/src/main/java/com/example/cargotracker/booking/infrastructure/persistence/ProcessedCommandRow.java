package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 処理済みコマンドの表（`processed_command`）の 1 行（B-INV-03。Bolt 24）。結果の参照は「予約 ID:追跡番号」。
 */
public record ProcessedCommandRow(
        UUID commandId, String commandType, String payloadHash, String resultRef, OffsetDateTime processedAt) {}
