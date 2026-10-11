package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** identity.recovery_code の 1 行。 */
public record RecoveryCodeRow(UUID id, UUID userId, String codeHash, OffsetDateTime usedAt, OffsetDateTime issuedAt) {}
