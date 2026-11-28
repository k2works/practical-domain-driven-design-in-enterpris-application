package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 企業の種類（データモデル `company.kind`）。
 */
public enum CompanyKind {
    /** 荷主。 */
    SHIPPER,
    /** 荷受人。 */
    CONSIGNEE,
    /** A 社（運用する会社）。 */
    OPERATOR
}
