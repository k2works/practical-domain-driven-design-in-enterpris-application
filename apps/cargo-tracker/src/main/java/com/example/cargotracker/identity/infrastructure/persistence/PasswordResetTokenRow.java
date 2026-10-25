package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** identity.password_reset_token の 1 行。 */
public record PasswordResetTokenRow(
        UUID id,
        UUID requestId,
        UUID userId,
        String purpose,
        String tokenHash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime usedAt,
        OffsetDateTime invalidatedAt) {}
