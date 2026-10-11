package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** identity.login_failure の 1 行。 */
public record LoginFailureRow(UUID userId, int consecutiveFailures, OffsetDateTime lockedUntil) {}
