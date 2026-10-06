package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 監査記録の行（`identity.audit_record`）。
 */
public record AuditRecordRow(
        UUID id,
        OffsetDateTime occurredAt,
        UUID actorUserId,
        UUID actorCompanyId,
        String action,
        String result,
        String reason) {}
