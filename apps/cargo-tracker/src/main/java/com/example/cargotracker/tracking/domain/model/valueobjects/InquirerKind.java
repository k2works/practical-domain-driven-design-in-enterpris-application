package com.example.cargotracker.tracking.domain.model.valueobjects;

/** 照会者の種別（Bolt 40-15。表 {@code inquiry_record.inquirer_kind} の値）。 */
public enum InquirerKind {
    /** 荷主。 */
    SHIPPER,
    /** 荷受人。 */
    CONSIGNEE
}
