package com.example.cargotracker.tracking.domain.model.valueobjects;

/** 照会の経路（Bolt 40-15。表 {@code inquiry_record.channel} の値）。S-23 が書くのは電話とメールだけで、Web の 2 つは W9（US-21〜23）。 */
public enum InquiryChannel {
    /** 顧客 Web の自己照会。 */
    WEB_SELF,
    /** 顧客 Web の問い合わせ。 */
    WEB_INQUIRY,
    /** 電話。 */
    PHONE,
    /** メール。 */
    EMAIL
}
