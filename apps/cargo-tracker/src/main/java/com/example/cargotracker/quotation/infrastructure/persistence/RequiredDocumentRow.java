package com.example.cargotracker.quotation.infrastructure.persistence;

import java.util.UUID;

/**
 * 必要書類の表（`required_document`）の 1 行。
 */
public record RequiredDocumentRow(
        UUID transportRequestId,
        int versionNo,
        int documentNo,
        String documentType,
        String fileName,
        String mediaType,
        long sizeBytes,
        String sha256,
        String objectKey) {}
