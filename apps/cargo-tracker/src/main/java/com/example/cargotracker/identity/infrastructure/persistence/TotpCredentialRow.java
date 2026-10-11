package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** identity.totp_credential の 1 行。 */
public record TotpCredentialRow(UUID userId, String secretEncrypted, long lastUsedStep, OffsetDateTime enrolledAt) {}
