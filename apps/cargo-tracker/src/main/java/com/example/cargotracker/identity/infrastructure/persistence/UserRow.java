package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.UUID;

/**
 * 利用者の行（`identity.app_user`）。役割は `identity.user_role` から別に読む。
 */
public record UserRow(UUID id, UUID companyId, String email, String displayName, String passwordHash, String status) {}
