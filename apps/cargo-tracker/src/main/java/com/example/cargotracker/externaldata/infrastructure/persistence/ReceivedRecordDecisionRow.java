package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** external_data.received_record_decision の 1 行（Bolt 39d2）。 */
public record ReceivedRecordDecisionRow(
        UUID id, UUID receivedRecordId, String decision, UUID decidedBy, OffsetDateTime decidedAt, String reason) {}
