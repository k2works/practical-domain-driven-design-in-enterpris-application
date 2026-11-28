package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.UUID;

/**
 * 企業の行（`identity.company`）。
 */
public record CompanyRow(UUID id, String name, String kind, boolean active) {}
